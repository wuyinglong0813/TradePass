package com.tradepass.dto.response;

import java.util.List;

public record RolePayload(
        String id,
        String code,
        String name,
        List<String> permissions,
        boolean systemRole,
        boolean editable,
        boolean deletable
) {
}
