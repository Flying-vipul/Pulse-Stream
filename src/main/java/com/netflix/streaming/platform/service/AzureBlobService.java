package com.netflix.streaming.platform.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 *  AzureBlobService — PulseStream's Cloud Video Vault

 * Responsibilities:
 *   1. Upload an entire HLS folder (all .ts segments + all .m3u8 playlists)
 *      to Azure Blob Storage while preserving the folder structure.
 *   2. Return the public HTTPS URL of the master.m3u8 so React/HLS.js can stream it.
 *   3. Clean up local temp files after a successful upload.

 * The BlobServiceClient is auto-injected by Spring Cloud Azure using the
 * connection-string in application.yml — no manual auth code needed.
 */
@Service
public class AzureBlobService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobService.class);

    private final BlobServiceClient blobServiceClient;

    /**
     * The Azure container name, read from application.yml.
     * Must exactly match the container created in the Azure Portal.
     * e.g. "videos"
     */
    @Value("${app.azure.container-name}")
    private String containerName;

    // Spring injects the BlobServiceClient automatically via spring-cloud-azure-starter-storage
    public AzureBlobService(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Uploads every file inside {@code localHlsDirectory} to Azure Blob Storage
     * under the path {@code azureFolder/}.

     * Example:
     *   localHlsDirectory = "C:/Users/Vipul/Videos/PulseStream/movie_5"
     *   azureFolder       = "movies/movie_5"

     *   → Uploads: movies/movie_5/master.m3u8
     *              movies/movie_5/stream_0.m3u8
     *              movies/movie_5/stream_1.m3u8
     *              movies/movie_5/stream_2.m3u8
     *              movies/movie_5/stream_0_segment_000.ts  ...etc
     *
     * @param localHlsDirectory  Absolute Windows path to the FFmpeg output folder
     * @param azureFolder        The "virtual folder" prefix inside the container
     * @return                   Public HTTPS URL of the master.m3u8 playlist
     */
    public String uploadHlsFolderToAzure(String localHlsDirectory, String azureFolder) throws IOException {

        Path sourceDir = Paths.get(localHlsDirectory);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException(
                    "HLS source directory does not exist: " + localHlsDirectory);
        }

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        //  1. Auto-create the container if it doesn't exist yet
        if (!containerClient.exists()) {
            log.info(" Container '{}' not found — creating it now...", containerName);
            containerClient.create();
            log.info(" Container '{}' created successfully!", containerName);
        }

        //  2. FORCE CONFIGURE CORS EVERY TIME TO FIX THE BROWSER ERROR PERMANENTLY!
        try {
            com.azure.storage.blob.models.BlobServiceProperties properties = blobServiceClient.getProperties();
            com.azure.storage.blob.models.BlobCorsRule corsRule = new com.azure.storage.blob.models.BlobCorsRule()
                    .setAllowedOrigins("*")
                    .setAllowedMethods("GET,OPTIONS")
                    .setAllowedHeaders("*")
                    .setExposedHeaders("*")
                    .setMaxAgeInSeconds(86400);
            
            properties.setCors(List.of(corsRule));
            blobServiceClient.setProperties(properties);
            log.info(" Azure CORS rules automatically applied! Browser streams will now work.");
        } catch (Exception e) {
            log.error("Could not auto-configure CORS: {}", e.getMessage());
        }

        // Collect all files inside the HLS folder (non-recursive first, but handles sub-folders too)
        List<Path> filesToUpload;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            filesToUpload = stream.filter(Files::isRegularFile).toList();
        }

        if (filesToUpload.isEmpty()) {
            throw new IllegalStateException("No HLS files found in: " + localHlsDirectory);
        }

        log.info("  Starting Azure upload: {} files → container '{}' / '{}'",
                filesToUpload.size(), containerName, azureFolder);

        String masterBlobUrl = null;

        for (Path filePath : filesToUpload) {
            // Build the blob name: e.g. "movies/movie_5/stream_0_segment_003.ts"
            String relativeName = sourceDir.relativize(filePath).toString()
                    .replace("\\", "/");  // always use forward slashes for blob paths
            String blobName = azureFolder + "/" + relativeName;

            BlobClient blobClient = containerClient.getBlobClient(blobName);

            log.info("  ↑ Uploading: {} → {}", relativeName, blobName);

            // 'true' = overwrite if blob already exists (safe for re-uploads)
            blobClient.uploadFromFile(filePath.toString(), true);

            // Capture the master playlist URL
            if (relativeName.equals("master.m3u8")) {
                try {
                    masterBlobUrl = java.net.URLDecoder.decode(blobClient.getBlobUrl(), java.nio.charset.StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    masterBlobUrl = blobClient.getBlobUrl(); // Fallback if decode fails
                }
                log.info("  Master Playlist URL: {}", masterBlobUrl);
            }
        }

        if (masterBlobUrl == null) {
            throw new IllegalStateException(
                    "Upload completed but master.m3u8 was not found in: " + localHlsDirectory);
        }

        log.info("Azure upload complete! {} files uploaded successfully.", filesToUpload.size());
        return masterBlobUrl;
    }

    /**
     * Uploads a single file to Azure Blob Storage.
     * Used for uploading individual .m3u8 or .ts files if needed.
     *
     * @param localFilePath  Absolute Windows path to the file
     * @param blobName       The full blob path inside the container (e.g. "movies/movie_5/master.m3u8")
     * @return               Public HTTPS URL of the uploaded blob
     */
    public String uploadSingleFile(String localFilePath, String blobName) {
        log.info("  Uploading single file: {} → {}", localFilePath, blobName);

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.uploadFromFile(localFilePath, true);

        String finalUrl = blobClient.getBlobUrl();
        try {
            finalUrl = java.net.URLDecoder.decode(finalUrl, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {}
        
        log.info(" Uploaded: {}", finalUrl);
        return finalUrl;
    }

    /**
     * Deletes a local directory and all its contents after a successful Azure upload.
     * This frees up local disk space — the video now lives safely in Azure.

     * @param localDirectory  The local folder to delete (e.g. the HLS output or temp MP4 folder)
     */
    public void cleanupLocalDirectory(String localDirectory) {
        Path dir = Paths.get(localDirectory);
        if (!Files.exists(dir)) return;

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())  // delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("️  Could not delete local file: {} — {}", path, e.getMessage());
                        }
                    });
            log.info("  Cleaned up local directory: {}", localDirectory);
        } catch (IOException e) {
            log.warn("  Could not fully clean up directory: {} — {}", localDirectory, e.getMessage());
        }
    }
}
