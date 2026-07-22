package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.entity.AuditLog;
import com.tradepass.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void log(long companyId, String bizType, Object bizId, String action, String detail) {
        logAs(companyId, AuthContext.userId(), bizType, bizId, action, detail);
    }

    public void logAs(long companyId, long userId, String bizType, Object bizId, String action, String detail) {
        AuditLog audit = new AuditLog();
        audit.setCompanyId(companyId);
        audit.setUserId(userId);
        audit.setBizType(bizType);
        audit.setBizId(String.valueOf(bizId));
        audit.setAction(action);
        audit.setDetail(detail);
        auditLogMapper.insert(audit);
    }
}
