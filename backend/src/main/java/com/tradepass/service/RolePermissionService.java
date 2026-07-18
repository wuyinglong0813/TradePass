package com.tradepass.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RolePermissionService {
    public record RoleDef(String text, List<String> permissions) {
    }

    private static final Map<String, RoleDef> ROLES = Map.of(
            "LEGAL", new RoleDef("法人", List.of("all")),
            "ADMIN", new RoleDef("管理员", List.of("member_manage", "auth_manage", "company_manage", "seal_manage", "contract_template")),
            "SALES", new RoleDef("销售员", List.of("supplier_view", "counterparty_manage", "order_view",
                    "order_create", "contract_sign", "contract_view", "reconciliation")),
            "PURCHASER", new RoleDef("采购员", List.of("buyer_view", "order_create", "contract_view",
                    "order_view", "contract_sign", "reconciliation")),
            "FINANCE", new RoleDef("财务", List.of("invoice_view", "reconciliation")),
            "GUEST", new RoleDef("访客", List.of()),
            "LEGAL_CANDIDATE", new RoleDef("法人认证待审核", List.of())
    );

    public RoleDef role(String code) {
        if (code == null || code.isBlank()) {
            return ROLES.get("GUEST");
        }
        return ROLES.getOrDefault(code, new RoleDef(code, List.of()));
    }

    public String roleText(String code) {
        return role(code).text();
    }
}
