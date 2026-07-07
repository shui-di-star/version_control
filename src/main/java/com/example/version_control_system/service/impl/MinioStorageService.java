package com.example.version_control_system.service.impl;

import com.example.version_control_system.common.ResultCode;
import com.example.version_control_system.config.MinioProperties;
import com.example.version_control_system.exception.BusinessException;
import com.example.version_control_system.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/** MinIO 对象存储实现。首次使用惰性建 bucket。 */
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(MinioClient client, MinioProperties props) {
        this.client = client;
        this.bucket = props.getBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "对象存储 bucket 初始化失败：" + e.getMessage());
        }
    }

    @Override
    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件下载失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件删除失败：" + e.getMessage());
        }
    }
}
