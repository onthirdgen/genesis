package com.callaudit.ingestion.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import io.minio.GetObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for StorageService
 *
 * These tests mock MinIO client interactions to test storage logic without requiring an actual MinIO server.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private StorageService storageService;

    @Captor
    private ArgumentCaptor<PutObjectArgs> putObjectArgsCaptor;

    @Captor
    private ArgumentCaptor<GetObjectArgs> getObjectArgsCaptor;

    @Captor
    private ArgumentCaptor<StatObjectArgs> statObjectArgsCaptor;

    @Captor
    private ArgumentCaptor<BucketExistsArgs> bucketExistsArgsCaptor;

    @Captor
    private ArgumentCaptor<MakeBucketArgs> makeBucketArgsCaptor;

    private static final String BUCKET_NAME = "calls";
    private static final String MINIO_ENDPOINT = "http://localhost:9000";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(storageService, "minioEndpoint", MINIO_ENDPOINT);
    }

    @Test
    void uploadFile_BucketExists_UploadSuccessful() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        String contentType = "audio/wav";
        long fileSize = 1024L;
        String fileExtension = "wav";
        InputStream inputStream = new ByteArrayInputStream("test audio content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // Act
        String result = storageService.uploadFile(callId, inputStream, contentType, fileSize, fileExtension);

        // Assert
        assertNotNull(result);
        assertThat(result).startsWith(MINIO_ENDPOINT);
        assertThat(result).contains(BUCKET_NAME);
        assertThat(result).contains(callId.toString());
        assertThat(result).endsWith(".wav");

        // Verify bucket existence check
        verify(minioClient).bucketExists(bucketExistsArgsCaptor.capture());
        assertEquals(BUCKET_NAME, bucketExistsArgsCaptor.getValue().bucket());

        // Verify file upload
        verify(minioClient).putObject(putObjectArgsCaptor.capture());
        PutObjectArgs putArgs = putObjectArgsCaptor.getValue();
        assertEquals(BUCKET_NAME, putArgs.bucket());
        assertThat(putArgs.object()).contains(callId.toString());
        assertThat(putArgs.object()).endsWith(".wav");
    }

    @Test
    void uploadFile_BucketDoesNotExist_CreatesBucketThenUploads() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        String contentType = "audio/mp3";
        long fileSize = 2048L;
        String fileExtension = "mp3";
        InputStream inputStream = new ByteArrayInputStream("test audio content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // Act
        String result = storageService.uploadFile(callId, inputStream, contentType, fileSize, fileExtension);

        // Assert
        assertNotNull(result);
        assertThat(result).contains(callId.toString());
        assertThat(result).endsWith(".mp3");

        // Verify bucket creation
        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient).makeBucket(makeBucketArgsCaptor.capture());
        assertEquals(BUCKET_NAME, makeBucketArgsCaptor.getValue().bucket());

        // Verify file upload
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_GeneratesCorrectObjectName() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // Act
        storageService.uploadFile(callId, inputStream, "audio/wav", 100L, "wav");

        // Assert
        verify(minioClient).putObject(putObjectArgsCaptor.capture());
        String objectName = putObjectArgsCaptor.getValue().object();

        // Object name should follow format: {year}/{month}/{callId}.{ext}
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        String expectedPrefix = String.format("%d/%02d/", now.getYear(), now.getMonthValue());

        assertThat(objectName).startsWith(expectedPrefix);
        assertThat(objectName).contains(callId.toString());
        assertThat(objectName).endsWith(".wav");
    }

    @Test
    void uploadFile_DifferentFileFormats_GeneratesCorrectExtensions() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // Test different formats
        String[] extensions = {"wav", "mp3", "m4a", "flac", "ogg"};
        String[] contentTypes = {"audio/wav", "audio/mpeg", "audio/mp4", "audio/flac", "audio/ogg"};

        for (int i = 0; i < extensions.length; i++) {
            InputStream inputStream = new ByteArrayInputStream("content".getBytes());

            // Act
            String result = storageService.uploadFile(callId, inputStream, contentTypes[i], 100L, extensions[i]);

            // Assert
            assertThat(result).endsWith("." + extensions[i]);
        }

        verify(minioClient, times(extensions.length)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_MinioThrowsException_ThrowsRuntimeException() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class)))
            .thenThrow(new RuntimeException("MinIO connection failed"));

        // Act & Assert
        assertThatThrownBy(() -> storageService.uploadFile(callId, inputStream, "audio/wav", 100L, "wav"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to upload file to storage");
    }

    @Test
    void downloadFile_FileExists_ReturnsInputStream() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        String fileExtension = "wav";
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        // Act
        InputStream result = storageService.downloadFile(callId, fileExtension);

        // Assert
        assertNotNull(result);
        assertEquals(mockResponse, result);

        verify(minioClient).getObject(getObjectArgsCaptor.capture());
        GetObjectArgs args = getObjectArgsCaptor.getValue();
        assertEquals(BUCKET_NAME, args.bucket());
        assertThat(args.object()).contains(callId.toString());
        assertThat(args.object()).endsWith(".wav");
    }

    @Test
    void downloadFile_GeneratesCorrectObjectName() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        // Act
        storageService.downloadFile(callId, "mp3");

        // Assert
        verify(minioClient).getObject(getObjectArgsCaptor.capture());
        String objectName = getObjectArgsCaptor.getValue().object();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        String expectedPrefix = String.format("%d/%02d/", now.getYear(), now.getMonthValue());

        assertThat(objectName).startsWith(expectedPrefix);
        assertThat(objectName).contains(callId.toString());
        assertThat(objectName).endsWith(".mp3");
    }

    @Test
    void downloadFile_MinioThrowsException_ThrowsRuntimeException() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();

        when(minioClient.getObject(any(GetObjectArgs.class)))
            .thenThrow(new RuntimeException("File not found in MinIO"));

        // Act & Assert
        assertThatThrownBy(() -> storageService.downloadFile(callId, "wav"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to download file from storage");
    }

    @Test
    void fileExists_FilePresent_ReturnsTrue() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        String fileExtension = "wav";

        // Mock successful statObject (file exists)
        when(minioClient.statObject(any(StatObjectArgs.class)))
            .thenReturn(null); // statObject just needs to not throw an exception

        // Act
        boolean result = storageService.fileExists(callId, fileExtension);

        // Assert
        assertTrue(result);

        verify(minioClient).statObject(statObjectArgsCaptor.capture());
        StatObjectArgs args = statObjectArgsCaptor.getValue();
        assertEquals(BUCKET_NAME, args.bucket());
        assertThat(args.object()).contains(callId.toString());
        assertThat(args.object()).endsWith(".wav");
    }

    @Test
    void fileExists_FileNotPresent_ReturnsFalse() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        String fileExtension = "wav";

        // Mock statObject throwing exception (file doesn't exist)
        when(minioClient.statObject(any(StatObjectArgs.class)))
            .thenThrow(new RuntimeException("Object not found"));

        // Act
        boolean result = storageService.fileExists(callId, fileExtension);

        // Assert
        assertFalse(result);
    }

    @Test
    void fileExists_ChecksDifferentExtensions() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();

        when(minioClient.statObject(any(StatObjectArgs.class)))
            .thenReturn(null); // statObject just needs to not throw an exception

        // Act & Assert
        assertTrue(storageService.fileExists(callId, "wav"));
        assertTrue(storageService.fileExists(callId, "mp3"));
        assertTrue(storageService.fileExists(callId, "m4a"));

        verify(minioClient, times(3)).statObject(any(StatObjectArgs.class));
    }

    @Test
    void uploadFile_BucketExistsCheckFails_ThrowsRuntimeException() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
            .thenThrow(new RuntimeException("Unable to check bucket existence"));

        // Act & Assert
        assertThatThrownBy(() -> storageService.uploadFile(callId, inputStream, "audio/wav", 100L, "wav"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to upload file to storage")
            .cause()
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to ensure bucket exists")
                .cause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unable to check bucket existence");

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_BucketCreationFails_ThrowsRuntimeException() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doThrow(new RuntimeException("Failed to create bucket"))
            .when(minioClient).makeBucket(any(MakeBucketArgs.class));

        // Act & Assert
        assertThatThrownBy(() -> storageService.uploadFile(callId, inputStream, "audio/wav", 100L, "wav"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to upload file to storage")
            .cause()
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to ensure bucket exists")
                .cause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create bucket");

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_ReturnsCorrectUrlFormat() throws Exception {
        // Arrange
        UUID callId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // Act
        String result = storageService.uploadFile(callId, inputStream, "audio/wav", 100L, "wav");

        // Assert
        // URL format should be: {endpoint}/{bucket}/{year}/{month}/{callId}.{ext}
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        String expectedPattern = String.format("%s/%s/%d/%02d/%s.wav",
            MINIO_ENDPOINT, BUCKET_NAME, now.getYear(), now.getMonthValue(), callId.toString());

        assertEquals(expectedPattern, result);
    }
}
