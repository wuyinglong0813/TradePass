package com.tradepass.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionServiceTest {
    private final RolePermissionService service = new RolePermissionService();

    @Test
    void returnsBuiltInRolePermissions() {
        assertThat(service.roleText("LEGAL")).isEqualTo("法人");
        assertThat(service.role("ADMIN").permissions())
                .contains("member_manage", "contract_template");
    }

    @Test
    void fallsBackForBlankAndCustomRoleCodes() {
        assertThat(service.role(null).text()).isEqualTo("访客");
        assertThat(service.role(" ").permissions()).isEmpty();
        assertThat(service.role("AUDITOR").text()).isEqualTo("AUDITOR");
        assertThat(service.role("AUDITOR").permissions()).isEmpty();
    }
}
