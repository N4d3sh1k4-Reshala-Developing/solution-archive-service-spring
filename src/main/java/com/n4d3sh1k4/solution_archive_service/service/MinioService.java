package com.n4d3sh1k4.solution_archive_service.service;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String tempBucket;
    private final String datasetBucket;

    public MinioService(
            @Value("${minio.url}") String url,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.temp-bucket}") String tempBucket,
            @Value("${minio.dataset-bucket}") String datasetBucket) {
        
        this.minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
        this.tempBucket = tempBucket;
        this.datasetBucket = datasetBucket;
    }

    @PostConstruct
    public void initBuckets() {
        createBucketIfNotExists(tempBucket);
        createBucketIfNotExists(datasetBucket);
    }

    private void createBucketIfNotExists(String bucketName) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket '{}' already exists.", bucketName);
            }
        } catch (Exception e) {
            log.error("Error creating MinIO bucket {}", bucketName, e);
        }
    }

    public String saveToTempBucket(MultipartFile file) {
        String filename = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(tempBucket)
                            .object(filename)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return filename;
        } catch (Exception e) {
            log.error("Error saving file to MinIO temp bucket", e);
            throw new RuntimeException("Could not save file to MinIO", e);
        }
    }

    public void deleteFromTempBucket(String filename) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(tempBucket)
                            .object(filename)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error deleting file from temp bucket", e);
        }
    }

    public void moveToDatasetBucket(String filename) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(datasetBucket)
                            .object(filename)
                            .source(CopySource.builder().bucket(tempBucket).object(filename).build())
                            .build()
            );
            deleteFromTempBucket(filename);
            log.info("Moved file {} from {} to {}", filename, tempBucket, datasetBucket);
        } catch (Exception e) {
            log.error("Error moving file to dataset bucket", e);
            throw new RuntimeException("Could not move file in MinIO", e);
        }
    }

    public InputStream download(String filename) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(datasetBucket)
                    .object(filename)
                    .build()
            );
        } catch (Exception e) {
            // В реальном проекте лучше создать кастомное исключение,
            // например, StorageException
            throw new RuntimeException("Ошибка при скачивании файла из MinIO: " + filename, e);
        }
    }
}
