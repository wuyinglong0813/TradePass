package com.tradepass.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.tradepass.common.BusinessException;
import com.tradepass.config.OssProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlibabaOssObjectStorageServiceTest {

    @Test
    void uploadsWithEncryptionAndReadsByVersionWithShaVerification() {
        OssProperties properties = properties();
        OSS client = mock(OSS.class);
        AtomicReference<PutObjectRequest> captured = new AtomicReference<>();
        PutObjectResult result = new PutObjectResult();
        result.setVersionId("v-1");
        result.setETag("etag-1");
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return result;
        });
        byte[] data = "%PDF-1.7 encrypted".getBytes();
        String sha256 = FileTypeInspector.sha256(data);
        AlibabaOssObjectStorageService service =
                new AlibabaOssObjectStorageService(properties, client);

        ObjectStorageService.StoredObject stored = service.putImmutable(
                "tradepass/contracts/8/v1/" + sha256 + ".pdf", data, "application/pdf", sha256);

        assertThat(stored.bucket()).isEqualTo("private-bucket");
        assertThat(stored.versionId()).isEqualTo("v-1");
        assertThat(stored.encryptionAlgorithm()).isEqualTo("AES256");
        assertThat(captured.get().getHeaders()).containsEntry("x-oss-forbid-overwrite", "true");
        assertThat(captured.get().getMetadata().getServerSideEncryption()).isEqualTo("AES256");
        assertThat(captured.get().getMetadata().getUserMetadata()).containsEntry("sha256", sha256);

        OSSObject object = new OSSObject();
        object.setObjectContent(new ByteArrayInputStream(data));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);
        assertThat(service.get(stored.reference())).containsExactly(data);
    }

    @Test
    void supportsKmsAndRejectsTamperedOrForeignObjects() {
        OssProperties properties = properties();
        properties.setServerSideEncryption("KMS");
        properties.setKmsKeyId("key-123");
        OSS client = mock(OSS.class);
        PutObjectResult result = new PutObjectResult();
        when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
        AlibabaOssObjectStorageService service =
                new AlibabaOssObjectStorageService(properties, client);
        byte[] data = new byte[]{1, 2, 3};
        String sha256 = FileTypeInspector.sha256(data);

        service.putImmutable("tradepass/logistics-documents/1/a.jpg",
                data, "image/jpeg", sha256);
        verify(client).putObject(any(PutObjectRequest.class));

        OSSObject tampered = new OSSObject();
        tampered.setObjectContent(new ByteArrayInputStream(new byte[]{3, 2, 1}));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(tampered);
        assertThatThrownBy(() -> service.get(new ObjectStorageService.ObjectReference(
                "private-bucket", "tradepass/logistics-documents/1/a.jpg", null, 3L, sha256)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完整性");
        assertThatThrownBy(() -> service.get(new ObjectStorageService.ObjectReference(
                "another-bucket", "tradepass/logistics-documents/1/a.jpg", null, 3L, sha256)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件存储位置不正确");
    }

    @Test
    void failsClosedForRequiredOrUnsafeConfiguration() {
        OssProperties disabled = new OssProperties();
        disabled.setRequired(true);
        assertThatThrownBy(() -> new AlibabaOssObjectStorageService(disabled))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须启用");

        OssProperties insecure = properties();
        insecure.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        assertThatThrownBy(() -> new AlibabaOssObjectStorageService(insecure, mock(OSS.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        OssProperties disabledDev = new OssProperties();
        AlibabaOssObjectStorageService fallback = new AlibabaOssObjectStorageService(disabledDev);
        assertThat(fallback.isEnabled()).isFalse();
        assertThatThrownBy(() -> fallback.putImmutable("tradepass/x", new byte[]{1},
                "application/octet-stream", FileTypeInspector.sha256(new byte[]{1})))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未启用");
    }

    private OssProperties properties() {
        OssProperties properties = new OssProperties();
        properties.setEnabled(true);
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("private-bucket");
        properties.setAccessKeyId("access-key-id");
        properties.setAccessKeySecret("access-key-secret");
        properties.setKeyPrefix("tradepass");
        return properties;
    }
}
