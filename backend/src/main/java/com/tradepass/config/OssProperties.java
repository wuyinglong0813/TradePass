package com.tradepass.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tradepass.oss")
public class OssProperties {
    private boolean enabled;
    private boolean required;
    private boolean migrateLegacyBlobs;
    private String endpoint = "";
    private String bucket = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String securityToken = "";
    private String serverSideEncryption = "AES256";
    private String kmsKeyId = "";
    private String keyPrefix = "tradepass";
    private int connectionTimeoutMillis = 5000;
    private int socketTimeoutMillis = 30000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isMigrateLegacyBlobs() { return migrateLegacyBlobs; }
    public void setMigrateLegacyBlobs(boolean migrateLegacyBlobs) { this.migrateLegacyBlobs = migrateLegacyBlobs; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getSecurityToken() { return securityToken; }
    public void setSecurityToken(String securityToken) { this.securityToken = securityToken; }
    public String getServerSideEncryption() { return serverSideEncryption; }
    public void setServerSideEncryption(String serverSideEncryption) { this.serverSideEncryption = serverSideEncryption; }
    public String getKmsKeyId() { return kmsKeyId; }
    public void setKmsKeyId(String kmsKeyId) { this.kmsKeyId = kmsKeyId; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public int getConnectionTimeoutMillis() { return connectionTimeoutMillis; }
    public void setConnectionTimeoutMillis(int connectionTimeoutMillis) { this.connectionTimeoutMillis = connectionTimeoutMillis; }
    public int getSocketTimeoutMillis() { return socketTimeoutMillis; }
    public void setSocketTimeoutMillis(int socketTimeoutMillis) { this.socketTimeoutMillis = socketTimeoutMillis; }
}
