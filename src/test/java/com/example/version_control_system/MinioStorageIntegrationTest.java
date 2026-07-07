package com.example.version_control_system;

import com.example.version_control_system.config.MinioProperties;
import com.example.version_control_system.service.StorageService;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 阶段八 8.1：MinIO 上传→下载→删除闭环。
 * <p><b>需 MinIO 环境</b>：无本地 MinIO（默认 :9000）时用 {@code Assumptions} 跳过（决策 8，非 Docker）。</p>
 */
@SpringBootTest
class MinioStorageIntegrationTest {

    @Autowired StorageService storageService;
    @Autowired MinioClient minioClient;
    @Autowired MinioProperties minioProperties;

    /** 探测 MinIO 健康端点是否可达；不可达则跳过本类测试。 */
    private boolean minioReachable() {
        try {
            URI uri = URI.create(minioProperties.getEndpoint() + "/minio/health/live");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void upload_download_delete_roundtrip() throws Exception {
        assumeTrue(minioReachable(), "MinIO 未运行（:9000），跳过需环境的测试");

        String key = "test/" + UUID.randomUUID() + ".txt";
        byte[] content = "hello minio".getBytes(StandardCharsets.UTF_8);

        storageService.upload(key, new ByteArrayInputStream(content), content.length, "text/plain");

        try (InputStream in = storageService.download(key)) {
            byte[] read = in.readAllBytes();
            org.assertj.core.api.Assertions.assertThat(read).isEqualTo(content);
        }

        storageService.delete(key);
    }
}
