package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.LogisticsDocument;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.LogisticsDocumentMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.config.OssProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LogisticsDocumentService {
    public static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    private final LogisticsDocumentMapper documentMapper;
    private final TradeContractMapper contractMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectStorageService objectStorageService;
    private final OssProperties ossProperties;

    @Autowired
    public LogisticsDocumentService(LogisticsDocumentMapper documentMapper,
                                    TradeContractMapper contractMapper,
                                    AccessControlService accessControlService,
                                    AuditLogService auditLogService,
                                    ObjectStorageService objectStorageService,
                                    OssProperties ossProperties) {
        this.documentMapper = documentMapper;
        this.contractMapper = contractMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.objectStorageService = objectStorageService;
        this.ossProperties = ossProperties;
    }

    LogisticsDocumentService(LogisticsDocumentMapper documentMapper,
                             TradeContractMapper contractMapper,
                             AccessControlService accessControlService,
                             AuditLogService auditLogService) {
        this(documentMapper, contractMapper, accessControlService, auditLogService, null, null);
    }

    public List<Map<String, Object>> listDocuments(Long contractId) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        requireContractParty(contractId, companyId);
        return documentMapper.selectList(new LambdaQueryWrapper<LogisticsDocument>()
                        .select(LogisticsDocument::getId,
                                LogisticsDocument::getCompanyId,
                                LogisticsDocument::getContractId,
                                LogisticsDocument::getOriginalName,
                                LogisticsDocument::getContentType,
                                LogisticsDocument::getFileSize,
                                LogisticsDocument::getCreatedBy,
                                LogisticsDocument::getCreatedAt)
                        .eq(LogisticsDocument::getContractId, contractId)
                        .orderByDesc(LogisticsDocument::getCreatedAt)
                        .orderByDesc(LogisticsDocument::getId))
                .stream()
                .map(this::documentView)
                .toList();
    }

    @Transactional
    public Map<String, Object> upload(Long contractId, String originalName, byte[] imageData) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_sign", "order_create");
        requireContractParty(contractId, companyId);
        if (imageData == null || imageData.length == 0) {
            throw new BusinessException("请选择物流单图片");
        }
        if (imageData.length > MAX_IMAGE_SIZE) {
            throw new BusinessException("物流单图片不能超过 10MB");
        }
        String contentType = detectImageContentType(imageData);
        if (contentType == null) {
            throw new BusinessException("仅支持 JPG、PNG、GIF 或 WebP 图片");
        }

        LogisticsDocument document = new LogisticsDocument();
        document.setCompanyId(companyId);
        document.setContractId(contractId);
        document.setOriginalName(normalizeFileName(originalName, contentType));
        document.setContentType(contentType);
        document.setFileSize((long) imageData.length);
        String sha256 = FileTypeInspector.sha256(imageData);
        document.setSha256(sha256);
        ObjectStorageService.StoredObject stored = store(companyId, contractId,
                contentType, imageData, sha256);
        if (stored == null) {
            document.setImageData(imageData);
        } else {
            document.setStorageProvider(stored.provider());
            document.setStorageBucket(stored.bucket());
            document.setObjectKey(stored.objectKey());
            document.setObjectVersionId(stored.versionId());
            document.setEtag(stored.etag());
            document.setEncryptionAlgorithm(stored.encryptionAlgorithm());
        }
        document.setCreatedBy(AuthContext.userId());
        documentMapper.insert(document);
        auditLogService.log(companyId, "LOGISTICS_DOCUMENT", document.getId(),
                "UPLOAD", "上传合同物流单图片 " + document.getOriginalName());

        LogisticsDocument created = documentMapper.selectById(document.getId());
        return documentView(created == null ? document : created);
    }

    public LogisticsDocument getImage(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        LogisticsDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException("物流单图片不存在");
        }
        requireContractParty(document.getContractId(), companyId);
        if (document.getImageData() == null) {
            if (objectStorageService == null || !objectStorageService.isEnabled()
                    || document.getObjectKey() == null) {
                throw new BusinessException("物流单图片暂不可用，请联系管理员");
            }
            document.setImageData(objectStorageService.get(new ObjectStorageService.ObjectReference(
                    document.getStorageBucket(), document.getObjectKey(), document.getObjectVersionId(),
                    document.getFileSize(), document.getSha256())));
        }
        return document;
    }

    private ObjectStorageService.StoredObject store(long companyId, Long contractId, String contentType,
                                                     byte[] data, String sha256) {
        if (objectStorageService == null || !objectStorageService.isEnabled()) return null;
        java.time.LocalDate today = java.time.LocalDate.now();
        String key = keyPrefix() + "/file/" + companyId + "/" + contractId
                + "/logistics/" + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/"
                + UUID.randomUUID() + "-" + sha256 + "."
                + FileTypeInspector.extension(contentType);
        return objectStorageService.putImmutable(key, data, contentType, sha256);
    }

    private String keyPrefix() {
        String value = ossProperties == null ? "tradepass" : ossProperties.getKeyPrefix();
        value = value == null ? "" : value.trim().replaceAll("^/+|/+$", "");
        return value.isBlank() ? "tradepass" : value;
    }

    private TradeContract requireContractParty(Long contractId, long companyId) {
        TradeContract contract = contractMapper.selectById(contractId);
        if (contract == null
                || (!Long.valueOf(companyId).equals(contract.getCompanyId())
                && !Long.valueOf(companyId).equals(contract.getCounterpartyCompanyId()))) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    private Map<String, Object> documentView(LogisticsDocument document) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", document.getId());
        view.put("contractId", document.getContractId());
        view.put("uploaderCompanyId", document.getCompanyId());
        view.put("originalName", document.getOriginalName());
        view.put("contentType", document.getContentType());
        view.put("fileSize", document.getFileSize());
        view.put("createdAt", document.getCreatedAt());
        return view;
    }

    private String normalizeFileName(String originalName, String contentType) {
        String name = originalName == null ? "" : originalName
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        if (name.isBlank()) {
            name = "物流单." + extension(contentType);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String detectImageContentType(byte[] data) {
        if (data.length >= 3
                && unsigned(data[0]) == 0xff
                && unsigned(data[1]) == 0xd8
                && unsigned(data[2]) == 0xff) {
            return "image/jpeg";
        }
        if (data.length >= 8
                && unsigned(data[0]) == 0x89
                && data[1] == 'P'
                && data[2] == 'N'
                && data[3] == 'G'
                && unsigned(data[4]) == 0x0d
                && unsigned(data[5]) == 0x0a
                && unsigned(data[6]) == 0x1a
                && unsigned(data[7]) == 0x0a) {
            return "image/png";
        }
        if (data.length >= 6) {
            String signature = new String(data, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(signature) || "GIF89a".equals(signature)) {
                return "image/gif";
            }
        }
        if (data.length >= 12
                && "RIFF".equals(new String(data, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(data, 8, 4, StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        return null;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}
