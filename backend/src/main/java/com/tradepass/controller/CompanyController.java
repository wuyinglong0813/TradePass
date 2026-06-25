package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.AuthorizationRecord;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.SealRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CompanyController {

    private final JdbcTemplate db;

    static final Map<String, String> ROLE_TEXTS = Map.of(
            "LEGAL", "法人", "ADMIN", "管理员", "SALES", "销售员",
            "PURCHASER", "采购员", "FINANCE", "财务"
    );

    public CompanyController(JdbcTemplate db) {
        this.db = db;
    }

    // ================================================================
    // 企业信息（走数据库）
    // ================================================================

    @GetMapping("/companies/{id}")
    public ApiResponse<CompanyProfile> getCompany(@PathVariable String id) {
        return ApiResponse.ok(loadCompany(id));
    }

    @PostMapping("/companies")
    public ApiResponse<CompanyProfile> submitCompany(@Valid @RequestBody CompanySubmitRequest request) {
        String id = request.id() != null && !request.id().isBlank() ? request.id() : UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        db.update("INSERT INTO company (id, name, credit_code, legal_person_name, certification_status, real_name_status, face_status, seal_status) VALUES (?, ?, ?, ?, 'PENDING', 'NOT_STARTED', 'NOT_STARTED', 'NOT_UPLOADED') ON DUPLICATE KEY UPDATE name=VALUES(name), credit_code=VALUES(credit_code), legal_person_name=VALUES(legal_person_name)",
                Long.parseLong(id), request.name(), request.creditCode(), request.legalPersonName());
        return ApiResponse.ok(loadCompany(id));
    }

    @PostMapping("/companies/{id}/certifications")
    public ApiResponse<CompanyProfile> submitCertification(@PathVariable String id) {
        db.update("UPDATE company SET certification_status = 'PENDING_REVIEW' WHERE id = ?", Long.parseLong(id));
        return ApiResponse.ok(loadCompany(id));
    }

    @PostMapping("/verifications/real-name")
    public ApiResponse<CompanyProfile> verifyRealName(@Valid @RequestBody VerificationRequest req) {
        db.update("UPDATE company SET real_name_status = 'VERIFIED' WHERE id = ?", Long.parseLong(req.companyId()));
        return ApiResponse.ok(loadCompany(req.companyId()));
    }

    @PostMapping("/verifications/face")
    public ApiResponse<CompanyProfile> verifyFace(@Valid @RequestBody VerificationRequest req) {
        db.update("UPDATE company SET face_status = 'VERIFIED' WHERE id = ?", Long.parseLong(req.companyId()));
        return ApiResponse.ok(loadCompany(req.companyId()));
    }

    @PostMapping("/seals")
    public ApiResponse<SealRecord> uploadSeal(@Valid @RequestBody SealRequest req) {
        db.update("UPDATE company SET seal_status = 'PENDING_REVIEW' WHERE id = ?", Long.parseLong(req.companyId()));
        return ApiResponse.ok(new SealRecord(UUID.randomUUID().toString(), req.companyId(), req.fileUrl(), req.usage(), "PENDING_REVIEW"));
    }

    // ================================================================
    // 邀请码
    // ================================================================

    @PostMapping("/companies/invite")
    public ApiResponse<InviteResult> createInvite(@Valid @RequestBody InviteRequest req) {
        // 验证是该公司管理员/法人
        var member = db.queryForList("SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE' AND (role_code = 'LEGAL' OR role_code = 'ADMIN')",
                Long.parseLong(req.companyId()), req.userId());
        if (member.isEmpty()) throw new BusinessException("无权生成邀请码");

        String code = generateInviteCode();
        db.update("INSERT INTO company_invite (company_id, code, type, expires_at) VALUES (?, ?, 'member', DATE_ADD(NOW(), INTERVAL 24 HOUR))",
                Long.parseLong(req.companyId()), code);
        return ApiResponse.ok(new InviteResult(code, req.companyId()));
    }

    /** 供方公司邀请（仅法人） */
    @PostMapping("/companies/counterparty-invite")
    public ApiResponse<InviteResult> createCounterpartyInvite(@Valid @RequestBody InviteRequest req) {
        var member = db.queryForList("SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE' AND role_code = 'LEGAL'",
                Long.parseLong(req.companyId()), req.userId());
        if (member.isEmpty()) throw new BusinessException("仅法人可邀请供方公司");

        String code = generateInviteCode();
        db.update("INSERT INTO company_invite (company_id, code, type, expires_at) VALUES (?, ?, 'counterparty', DATE_ADD(NOW(), INTERVAL 24 HOUR))",
                Long.parseLong(req.companyId()), code);
        return ApiResponse.ok(new InviteResult(code, req.companyId()));
    }

    // ================================================================
    // 加入企业（用邀请码）
    // ================================================================

    @PostMapping("/companies/join")
    public ApiResponse<JoinResult> joinCompany(@Valid @RequestBody JoinRequest req) {
        // 查邀请码
        var invites = db.queryForList("SELECT id, company_id, type, used, expires_at FROM company_invite WHERE code = ?", req.code());
        if (invites.isEmpty()) throw new BusinessException("邀请码无效");
        var inv = invites.get(0);
        if ((Boolean) inv.get("used")) throw new BusinessException("邀请码已被使用");
        if (((java.sql.Timestamp) inv.get("expires_at")).before(new java.util.Date())) throw new BusinessException("邀请码已过期");

        long companyId = ((Number) inv.get("company_id")).longValue();
        String type = (String) inv.getOrDefault("type", "member");
        long userId = req.userId();

        if ("counterparty".equals(type)) {
            // 供方邀请：受邀人必须是其公司的法人
            var legalCheck = db.queryForList("SELECT id, company_id FROM company_member WHERE user_id = ? AND role_code = 'LEGAL' AND status = 'ACTIVE'", userId);
            if (legalCheck.isEmpty()) throw new BusinessException("仅公司法人可接受供方邀请，请先在您的公司完成法人认证");
            // 直接绑定为供方关系（自动通过，无需审批）
            long userCompanyId = ((Number) legalCheck.get(0).get("company_id")).longValue();
            var co = db.queryForMap("SELECT name FROM company WHERE id = ?", userCompanyId);
            String coName = co != null ? (String) co.get("name") : "未知企业";
            // 写入 counterparty 表（TradeController 的 counterparties）
            db.update("INSERT IGNORE INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (?, ?, 'SUPPLIER', 'ACTIVE')",
                    companyId, coName);
            db.update("UPDATE company_invite SET used = 1, used_by = ? WHERE id = ?", userId, inv.get("id"));
            return ApiResponse.ok(new JoinResult("ACTIVE", "已绑定供方关系：" + coName));
        }

        // 成员邀请：加入企业
        var exist = db.queryForList("SELECT id, status FROM company_member WHERE company_id = ? AND user_id = ?", companyId, userId);
        if (!exist.isEmpty()) {
            String st = (String) exist.get(0).get("status");
            if ("ACTIVE".equals(st)) throw new BusinessException("你已是该企业成员");
            if ("PENDING".equals(st)) throw new BusinessException("申请已提交，等待审批");
        }

        db.update("INSERT INTO company_member (company_id, user_id, role_code, status) VALUES (?, ?, 'GUEST', 'PENDING')", companyId, userId);
        db.update("UPDATE company_invite SET used = 1, used_by = ? WHERE id = ?", userId, inv.get("id"));
        return ApiResponse.ok(new JoinResult("PENDING", "申请已提交，等待管理员审批"));
    }

    // ================================================================
    // 授权管理（按企业区分）
    // ================================================================

    @GetMapping("/authorizations")
    public ApiResponse<List<AuthorizationRecord>> listMembers(@RequestParam(defaultValue = "1") String companyId) {
        List<AuthorizationRecord> list = db.query(
                "SELECT m.id, m.user_id, m.role_code, m.status, u.nickname, u.phone FROM company_member m JOIN sys_user u ON m.user_id = u.id WHERE m.company_id = ? ORDER BY m.status, m.id",
                (rs, rowNum) -> {
                    String roleCode = rs.getString("role_code");
                    String roleText = ROLE_TEXTS.getOrDefault(roleCode, roleCode);
                    String name = rs.getString("nickname");
                    String phone = rs.getString("phone");
                    // 显示名：优先昵称，否则用手机号后四位，都没有则显示 openid 提示
                    String displayName = (name != null && !name.isBlank() && !"新用户".equals(name))
                            ? name
                            : (phone != null && !phone.isBlank() ? "用户" + phone.substring(phone.length() - 4) : "微信用户");
                    return new AuthorizationRecord(
                            String.valueOf(rs.getLong("id")), companyId, String.valueOf(rs.getLong("user_id")),
                            displayName, roleCode, roleText, List.of(), rs.getString("status"),
                            phone != null ? phone : ""
                    );
                },
                Long.parseLong(companyId));
        return ApiResponse.ok(list);
    }

    @PostMapping("/authorizations/{id}/approve")
    public ApiResponse<AuthorizationRecord> approveMember(@PathVariable String id, @Valid @RequestBody ApproveRequest req, @RequestParam(defaultValue = "1") String companyId) {
        String perms = req.customPermissions() != null && !req.customPermissions().isEmpty()
                ? String.join(",", req.customPermissions()) : null;
        db.update("UPDATE company_member SET role_code = ?, custom_permissions = ?, status = 'ACTIVE' WHERE id = ? AND company_id = ?",
                req.roleCode(), perms, Long.parseLong(id), Long.parseLong(companyId));
        return ApiResponse.ok(new AuthorizationRecord(id, companyId, "", "", req.roleCode(),
                ROLE_TEXTS.getOrDefault(req.roleCode(), req.roleCode()), List.of(), "ACTIVE", ""));
    }

    @PostMapping("/authorizations/{id}/reject")
    public ApiResponse<Void> rejectMember(@PathVariable String id, @RequestParam(defaultValue = "1") String companyId) {
        db.update("DELETE FROM company_member WHERE id = ? AND company_id = ? AND status = 'PENDING'", Long.parseLong(id), Long.parseLong(companyId));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/authorizations/{id}")
    public ApiResponse<Void> removeMember(@PathVariable String id, @RequestParam(defaultValue = "1") String companyId) {
        db.update("DELETE FROM company_member WHERE id = ? AND company_id = ? AND is_legal_person = 0", Long.parseLong(id), Long.parseLong(companyId));
        return ApiResponse.ok(null);
    }

    // ================================================================
    // 工具
    // ================================================================

    private CompanyProfile loadCompany(String id) {
        var rows = db.queryForList("SELECT * FROM company WHERE id = ?", Long.parseLong(id));
        if (rows.isEmpty()) throw new BusinessException("企业不存在");
        var r = rows.get(0);
        return new CompanyProfile(
                String.valueOf(r.get("id")), (String) r.get("name"), (String) r.get("credit_code"),
                (String) r.get("legal_person_name"), (String) r.get("certification_status"),
                (String) r.get("real_name_status"), (String) r.get("face_status"), (String) r.get("seal_status")
        );
    }

    /** 生成 8 位随机邀请码 */
    private String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var sb = new StringBuilder();
        var rng = new java.security.SecureRandom();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ================================================================
    // 角色管理
    // ================================================================

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> listRoles(@RequestParam(defaultValue = "1") String companyId) {
        List<Map<String, Object>> roles = db.queryForList("SELECT id, name, permissions FROM role_def WHERE company_id = ? ORDER BY id", Long.parseLong(companyId));
        return ApiResponse.ok(roles);
    }

    @PostMapping("/roles")
    public ApiResponse<Map<String, Object>> createRole(@Valid @RequestBody RoleRequest req) {
        String json = "[\"" + String.join("\",\"", req.permissions()) + "\"]";
        db.update("INSERT INTO role_def (company_id, name, permissions) VALUES (?, ?, ?)",
                Long.parseLong(req.companyId()), req.name(), json);
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.ok(Map.of("id", id, "name", req.name(), "permissions", req.permissions()));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<Void> updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest req) {
        String json = "[\"" + String.join("\",\"", req.permissions()) + "\"]";
        db.update("UPDATE role_def SET name = ?, permissions = ? WHERE id = ?",
                req.name(), json, Long.parseLong(id));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable String id) {
        db.update("DELETE FROM role_def WHERE id = ?", Long.parseLong(id));
        return ApiResponse.ok(null);
    }

    // ---- DTO ----
    public record CompanySubmitRequest(String id, @NotBlank String name, @NotBlank String creditCode, @NotBlank String legalPersonName) {}
    public record VerificationRequest(@NotBlank String companyId) {}
    public record SealRequest(@NotBlank String companyId, @NotBlank String fileUrl, @NotBlank String usage) {}
    public record JoinRequest(@NotBlank String code, long userId) {}
    public record JoinResult(String status, String message) {}
    public record ApproveRequest(@NotBlank String roleCode, List<String> customPermissions) {}
    public record InviteRequest(@NotBlank String companyId, long userId) {}
    public record InviteResult(String code, String companyId) {}
    public record RoleRequest(@NotBlank String companyId, @NotBlank String name, @NotEmpty List<String> permissions) {}
}
