package com.mattoid.scheduled.storage.client;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mattoid.scheduled.storage.config.S3StorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class S3StorageClient implements StorageClient {

    private final S3StorageConfig config;
    private final AmazonS3 s3Client;

    public S3StorageClient(S3StorageConfig config) {
        this.config = config;
        BasicAWSCredentials credentials = new BasicAWSCredentials(config.getAccessKeyId(), config.getSecretAccessKey());
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials));

        if (StringUtils.hasText(config.getEndpoint())) {
            String region = StringUtils.hasText(config.getRegion()) ? config.getRegion() : "us-east-1";
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(config.getEndpoint().trim(), region));
        } else if (StringUtils.hasText(config.getRegion())) {
            builder.withRegion(config.getRegion());
        }

        if (Boolean.TRUE.equals(config.getPathStyleAccess())) {
            builder.withPathStyleAccessEnabled(true);
        }

        this.s3Client = builder.build();
    }

    @Override
    public String upload(File file, String filename) throws Exception {
        if (!StringUtils.hasText(filename)) {
            filename = file.getName();
        }
        filename = sanitizeFilename(filename);
        String objectKey = buildObjectKey(filename);
        s3Client.putObject(new PutObjectRequest(config.getBucketName(), objectKey, file));
        return buildUrl(objectKey);
    }

    @Override
    public void delete(String path) throws Exception {
        String objectKey = extractObjectKey(path);
        if (StringUtils.hasText(objectKey)) {
            s3Client.deleteObject(config.getBucketName(), objectKey);
        }
    }

    private String buildObjectKey(String filename) {
        String prefix = StringUtils.hasText(config.getPrefix()) ? config.getPrefix().trim() : "scheduled-task";
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        return prefix + "/" + UUID.randomUUID() + "/" + filename;
    }

    private String buildUrl(String objectKey) {
        Long expires = config.getSignedUrlExpires();
        if (expires != null && expires > 0) {
            Date expiration = new Date(System.currentTimeMillis() + expires * 1000);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(config.getBucketName(), objectKey)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(expiration);
            URL url = s3Client.generatePresignedUrl(request);
            return url.toString();
        }
        return s3Client.getUrl(config.getBucketName(), objectKey).toString();
    }

    private String extractObjectKey(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        // 简单实现：从 URL 中截取 bucket 后的路径
        String marker = "/" + config.getBucketName() + "/";
        int idx = url.indexOf(marker);
        if (idx >= 0) {
            return url.substring(idx + marker.length());
        }
        return null;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
