package com.tradepass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("company_member")
public class CompanyMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long userId;
    private String roleCode;
    private Boolean isLegalPerson;
    private Boolean isAdministrator;
    private String status;
    private String customPermissions;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Boolean getIsLegalPerson() { return isLegalPerson; }
    public void setIsLegalPerson(Boolean isLegalPerson) { this.isLegalPerson = isLegalPerson; }
    public Boolean getIsAdministrator() { return isAdministrator; }
    public void setIsAdministrator(Boolean isAdministrator) { this.isAdministrator = isAdministrator; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCustomPermissions() { return customPermissions; }
    public void setCustomPermissions(String customPermissions) { this.customPermissions = customPermissions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
