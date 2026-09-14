package com.tradepass.dto.response;

import java.util.List;

public record FadadaCompanyIdentityPayload(
        boolean enabled,
        String companyId,
        String status,
        String statusText,
        String verifiedName,
        String verifiedCreditCode,
        String failureReason,
        int enabledSealCount,
        List<SealPayload> seals,
        String lastSyncAt,
        String sealSyncWarning
) {
    public FadadaCompanyIdentityPayload(boolean enabled, String companyId, String status, String statusText,
                                       String verifiedName, String verifiedCreditCode, String failureReason,
                                       int enabledSealCount, List<SealPayload> seals, String lastSyncAt) {
        this(enabled, companyId, status, statusText, verifiedName, verifiedCreditCode, failureReason,
                enabledSealCount, seals, lastSyncAt, null);
    }
    public record SealPayload(String sealId, String sealName, String categoryType, String status) {}
}
