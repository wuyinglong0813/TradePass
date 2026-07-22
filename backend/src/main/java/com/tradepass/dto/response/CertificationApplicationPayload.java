package com.tradepass.dto.response;

public record CertificationApplicationPayload(
        String id,
        String companyId,
        String companyName,
        String providerRequestId,
        String status,
        String reviewReason,
        String submittedAt,
        String reviewedAt
) {
}
