package com.tradepass.dto.response;

public record PersonalIdentityPayload(
        boolean providerEnabled,
        String status,
        String statusText,
        String bindingStatus,
        String identStatus,
        String identProcessStatus,
        String verifiedName,
        String identMethod,
        String failureReason,
        String submittedAt,
        String verifiedAt,
        String lastSyncAt
) {
}
