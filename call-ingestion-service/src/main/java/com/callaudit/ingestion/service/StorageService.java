package com.callaudit.ingestion.service;

import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    /**
     * Upload a file to MinIO storage
     * Files are stored as: {year}/{month}/{callId}.{ext}
     *
     * @param callId UUID of the call
     * @param inputStream file input stream
     * @param contentType MIME type of the file
     * @param fileSize size of the file in bytes
     * @param fileExtension file extension (e.g., "wav", "mp3")
     * @return URL to access the file
     */
    public String uploadFile(UUID callId, InputStream inputStream, String contentType,
                           long fileSize, String fileExtension) {
        try {
            // Ensure bucket exists
            ensureBucketExists();

            // Generate object name with year/month prefix
            String objectName = generateObjectName(callId, fileExtension);

            log.info("Uploading file to MinIO: bucket={}, object={}", bucketName, objectName);

            // Upload file to MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, fileSize, -1)
                    .contentType(contentType)
                    .build()
            );

            log.info("Successfully uploaded file to MinIO: {}", objectName);

            // Return the URL to access the file
            return String.format("%s/%s/%s", minioEndpoint, bucketName, objectName);

        } catch (Exception e) {
            log.error("Error uploading file to MinIO for callId: {}", callId, e);
            throw new RuntimeException("Failed to upload file to storage", e);
        }
    }

    /**
     * Download a file from MinIO storage
     *
     * @param callId UUID of the call
     * @param fileExtension file extension
     * @return InputStream of the file
     */
    public InputStream downloadFile(UUID callId, String fileExtension) {
        try {
            String objectName = generateObjectName(callId, fileExtension);

            log.info("Downloading file from MinIO: bucket={}, object={}", bucketName, objectName);

            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );

        } catch (Exception e) {
            log.error("Error downloading file from MinIO for callId: {}", callId, e);
            throw new RuntimeException("Failed to download file from storage", e);
        }
    }

    /**
     * Check if a file exists in MinIO
     *
     * @param callId UUID of the call
     * @param fileExtension file extension
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(UUID callId, String fileExtension) {
        try {
            String objectName = generateObjectName(callId, fileExtension);
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensure the bucket exists, create it if it doesn't
     */
    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            );

            if (!found) {
                log.info("Bucket {} does not exist, creating it", bucketName);
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build()
                );
                log.info("Successfully created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Error checking/creating bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to ensure bucket exists", e);
        }
    }

    /**
     * Generate object name with year/month prefix
     * Format: {year}/{month}/{callId}.{ext}
     */
    private String generateObjectName(UUID callId, String fileExtension) {
        Instant now = Instant.now();
        int year = now.atZone(ZoneId.systemDefault()).getYear();
        int month = now.atZone(ZoneId.systemDefault()).getMonthValue();

        return String.format("%d/%02d/%s.%s", year, month, callId.toString(), fileExtension);
    }
}
