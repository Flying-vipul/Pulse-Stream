package com.netflix.streaming.platform.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    @Autowired
    private Cloudinary cloudinary;

    @Override
    public String uploadVideoLocally(String directoryName, MultipartFile file) throws IOException {

        // OS-AGNOSTIC: Works on Windows (local) AND Linux (Render)
        //    Windows → C:\Users\Vipul\AppData\Local\Temp\PulseStream\temp_videos
        //    Linux   → /tmp/PulseStream/temp_videos
        String basePath = System.getProperty("java.io.tmpdir") + "/PulseStream/" + directoryName;
        java.nio.file.Path storageDirectory = java.nio.file.Paths.get(basePath);

        // 2. Create the directory if it doesn't exist yet
        if (!java.nio.file.Files.exists(storageDirectory)) {
            java.nio.file.Files.createDirectories(storageDirectory);
        }

        // 3. Generate a secure, unique file name (e.g., 550e8400-e29b-41d4-a716-446655440000.mp4)
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";
        String uniqueFileName = java.util.UUID.randomUUID().toString() + fileExtension;

        // 4. Build the final absolute path
        java.nio.file.Path filePath = storageDirectory.resolve(uniqueFileName);

        // 5. Copy the Spring Boot memory stream to the physical Windows hard drive
        java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

//        Actually return the absolute Windows path so FFmpeg can read it!
        return filePath.toAbsolutePath().toString();
    }


    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {

        // 1. DEFENSE: Don't let users upload empty files!
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file to PulseStream.");
        }

        logger.info("Uploading image to Cloudinary folder: pulsestream/{}", path);

        try {
            // 2. THE UPGRADE: We pass the 'path' variable into Cloudinary's 'folder' settings.
            // If the controller passes "avatars", it creates a "pulsestream/avatars" folder!
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "pulsestream/" + path)
            );

            // 3. Extract and return the permanent, secure HTTPS URL
            String secureUrl = uploadResult.get("secure_url").toString();
            logger.info("Successfully uploaded image! URL: {}", secureUrl);

            return secureUrl;

        } catch (IOException e) {
            logger.error("Cloudinary upload failed: {}", e.getMessage());
            throw new IOException("Failed to upload image to PulseStream CDN.", e);
        }
    }
}