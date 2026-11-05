package com.netflix.streaming.platform.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCorsRule;
import com.azure.storage.blob.models.BlobServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;


@Configuration
public class AzureStorageCorsConfig {

    private static final Logger log = LoggerFactory.getLogger(AzureStorageCorsConfig.class);

    @Autowired
    private BlobServiceClient blobServiceClient;

    @Bean
    public ApplicationRunner configurAzureCorsOnStartup() {
        return args -> {
            log.info("Configuring Azure Blob Storage CORS on startup...");
            try {
                BlobServiceProperties properties = blobServiceClient.getProperties();

                BlobCorsRule corsRule = new BlobCorsRule()
                        .setAllowedOrigins("*")
                        .setAllowedMethods("GET,OPTIONS,HEAD")
                        .setAllowedHeaders("*")
                        .setExposedHeaders("Content-Length,Content-Range,Content-Type")
                        .setMaxAgeInSeconds(86400);

                properties.setCors(List.of(corsRule));
                blobServiceClient.setProperties(properties);

                log.info("Azure CORS configured! Browser video streaming is now enabled.");
            } catch (Exception e) {
                log.error("Failed to configure Azure CORS on startup: {}", e.getMessage());
                log.error("   Please configure CORS manually in the Azure Portal under:");
                log.error("   Storage Account → Resource sharing (CORS) → Blob service");
            }
        };
    }
}
