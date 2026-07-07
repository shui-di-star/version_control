package com.example.version_control_system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置：{@code app.jwt.secret} 签名密钥、{@code app.jwt.expiration-ms} 过期毫秒。
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC 签名密钥（至少 32 字节）。生产必须用环境变量覆盖。 */
    private String secret;

    /** token 有效期（毫秒）。 */
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
