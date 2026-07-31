package com.tradepass.service;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.tradepass.common.BusinessException;
import com.tradepass.config.OssProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Service
public class AlibabaOssObjectStorageService implements ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(AlibabaOssObjectStorageService.class);
    private static final String PROVIDER = "ALIYUN_OSS";
    private static final String FORBID_OVERWRITE_HEADER = "x-oss-forbid-overwrite";

    private final OssProperties properties;
    private final OSS client;
    private final String encryptionAlgorithm;

    @Autowired
    public AlibabaOssObjectStorageService(OssProperties properties) {
        this.properties = properties;
        if (properties.isRequired() && !properties.isEnabled()) {
            throw new IllegalStateException("生产环境必须启用阿里云 OSS 文件存储");
        }
        if (!properties.isEnabled()) {
            this.client = null;
            this.encryptionAlgorithm = normalizeEncryption(properties.getServerSideEncryption());
            log.warn("阿里云 OSS 未启用，文件将继续使用仅限开发环境的 MySQL BLOB 兼容路径");
            return;
        }
        validateConfiguration(properties);
        this.encryptionAlgorithm = normalizeEncryption(properties.getServerSideEncryption());
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(Math.max(1000, properties.getConnectionTimeoutMillis()));
        configuration.setSocketTimeout(Math.max(1000, properties.getSocketTimeoutMillis()));
        configuration.setMaxErrorRetry(3);
        configuration.setCrcCheckEnabled(true);
        String endpoint = httpsEndpoint(properties.getEndpoint());
        if (hasText(properties.getSecurityToken())) {
            this.client = new OSSClientBuilder().build(endpoint,
                    properties.getAccessKeyId().trim(), properties.getAccessKeySecret(),
                    properties.getSecurityToken(), configuration);
        } else {
            this.client = new OSSClientBuilder().build(endpoint,
                    properties.getAccessKeyId().trim(), properties.getAccessKeySecret(), configuration);
        }
    }

    AlibabaOssObjectStorageService(OssProperties properties, OSS client) {
        this.properties = properties;
        validateConfiguration(properties);
        this.encryptionAlgorithm = normalizeEncryption(properties.getServerSideEncryption());
        this.client = client;
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public StoredObject putImmutable(String objectKey, byte[] data, String contentType, String sha256) {
        requireEnabled();
        validateObjectKey(objectKey);
        if (data == null || data.length == 0) {
            throw new BusinessException("不能上传空文件");
        }
        String actualSha = FileTypeInspector.sha256(data);
        if (!actualSha.equalsIgnoreCase(sha256)) {
            throw new BusinessException("文件摘要校验失败");
        }

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(contentType);
        metadata.setObjectAcl(CannedAccessControlList.Private);
        metadata.addUserMetadata("sha256", actualSha);
        metadata.addUserMetadata("storage-provider", PROVIDER);
        if ("KMS".equals(encryptionAlgorithm)) {
            metadata.setServerSideEncryption(ObjectMetadata.KMS_SERVER_SIDE_ENCRYPTION);
            if (hasText(properties.getKmsKeyId())) {
                metadata.setServerSideEncryptionKeyId(properties.getKmsKeyId().trim());
            }
        } else {
            metadata.setServerSideEncryption(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        }

        PutObjectRequest request = new PutObjectRequest(properties.getBucket().trim(), objectKey,
                new ByteArrayInputStream(data), metadata);
        request.addHeader(FORBID_OVERWRITE_HEADER, "true");
        try {
            PutObjectResult result = client.putObject(request);
            return new StoredObject(PROVIDER, properties.getBucket().trim(), objectKey,
                    blankToNull(result.getVersionId()), blankToNull(result.getETag()),
                    encryptionAlgorithm, data.length, actualSha);
        } catch (OSSException exception) {
            if ("FileAlreadyExists".equals(exception.getErrorCode())
                    || "ObjectAlreadyExists".equals(exception.getErrorCode())) {
                return existingImmutableObject(objectKey, data.length, actualSha);
            }
            log.error("OSS 上传失败，errorCode={}, requestId={}",
                    exception.getErrorCode(), exception.getRequestId());
            throw new BusinessException("文件安全存储失败，请稍后重试");
        } catch (RuntimeException exception) {
            log.error("OSS 上传连接失败", exception);
            throw new BusinessException("文件安全存储失败，请稍后重试");
        }
    }

    private StoredObject existingImmutableObject(String objectKey, long fileSize, String sha256) {
        try {
            ObjectMetadata metadata = client.getObjectMetadata(properties.getBucket().trim(), objectKey);
            String versionId = blankToNull(metadata.getVersionId());
            get(new ObjectReference(properties.getBucket().trim(), objectKey, versionId, fileSize, sha256));
            return new StoredObject(PROVIDER, properties.getBucket().trim(), objectKey, versionId,
                    blankToNull(metadata.getETag()), encryptionAlgorithm, fileSize, sha256);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("OSS 已存在对象的完整性复核失败，objectKey={}", objectKey, exception);
            throw new BusinessException("文件安全存储冲突，请联系管理员");
        }
    }

    @Override
    public byte[] get(ObjectReference reference) {
        requireEnabled();
        if (reference == null || !properties.getBucket().trim().equals(reference.bucket())) {
            throw new BusinessException("文件存储位置不正确");
        }
        validateObjectKey(reference.objectKey());
        GetObjectRequest request = hasText(reference.versionId())
                ? new GetObjectRequest(reference.bucket(), reference.objectKey(), reference.versionId())
                : new GetObjectRequest(reference.bucket(), reference.objectKey());
        try (OSSObject object = client.getObject(request)) {
            byte[] data = object.getObjectContent().readAllBytes();
            if (reference.fileSize() != null && reference.fileSize() >= 0
                    && data.length != reference.fileSize()) {
                throw new BusinessException("文件长度校验失败，请联系管理员");
            }
            String actualSha = FileTypeInspector.sha256(data);
            if (!hasText(reference.sha256()) || !actualSha.equalsIgnoreCase(reference.sha256())) {
                throw new BusinessException("文件完整性校验失败，请联系管理员");
            }
            return data;
        } catch (BusinessException exception) {
            throw exception;
        } catch (OSSException exception) {
            log.error("OSS 下载失败，errorCode={}, requestId={}",
                    exception.getErrorCode(), exception.getRequestId());
            throw new BusinessException("文件读取失败，请稍后重试");
        } catch (IOException | RuntimeException exception) {
            log.error("OSS 文件读取失败", exception);
            throw new BusinessException("文件读取失败，请稍后重试");
        }
    }

    public String keyPrefix() {
        String value = properties.getKeyPrefix() == null ? "" : properties.getKeyPrefix().trim();
        value = value.replaceAll("^/+|/+$", "");
        return value.isBlank() ? "tradepass" : value;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new BusinessException("文件安全存储服务尚未启用");
        }
    }

    private void validateConfiguration(OssProperties value) {
        if (!hasText(value.getEndpoint()) || !hasText(value.getBucket())
                || !hasText(value.getAccessKeyId()) || !hasText(value.getAccessKeySecret())) {
            throw new IllegalStateException("OSS 已启用，但 endpoint、bucket 或访问凭证未完整配置");
        }
        httpsEndpoint(value.getEndpoint());
        normalizeEncryption(value.getServerSideEncryption());
    }

    private String httpsEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.startsWith("http://")) {
            throw new IllegalStateException("OSS endpoint 必须使用 HTTPS");
        }
        return value.startsWith("https://") ? value : "https://" + value;
    }

    private String normalizeEncryption(String value) {
        String normalized = value == null ? "AES256" : value.trim().toUpperCase(Locale.ROOT);
        if (!"AES256".equals(normalized) && !"KMS".equals(normalized)) {
            throw new IllegalStateException("OSS 服务端加密仅支持 AES256 或 KMS");
        }
        return normalized;
    }

    private void validateObjectKey(String objectKey) {
        if (!hasText(objectKey) || objectKey.startsWith("/") || objectKey.contains("\\")
                || objectKey.contains("..") || objectKey.length() > 900) {
            throw new BusinessException("文件对象标识不正确");
        }
        String prefix = keyPrefix() + "/";
        if (!objectKey.startsWith(prefix)) {
            throw new BusinessException("文件对象不属于当前应用");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value : null;
    }
}
