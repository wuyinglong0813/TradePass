package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.AuthorizationRecord;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.CompanySearchSummary;
import com.tradepass.common.TradePassDtos.SealRecord;
import com.tradepass.dto.request.ApproveRequest;
import com.tradepass.dto.request.CompanySubmitRequest;
import com.tradepass.dto.request.InviteRequest;
import com.tradepass.dto.request.JoinRequest;
import com.tradepass.dto.request.RoleRequest;
import com.tradepass.dto.request.SealRequest;
import com.tradepass.dto.request.VerificationRequest;
import com.tradepass.dto.response.InviteResult;
import com.tradepass.dto.response.JoinResult;
import com.tradepass.dto.response.PagePayload;
import com.tradepass.dto.response.CertificationApplicationPayload;
import com.tradepass.dto.response.RolePayload;
import com.tradepass.service.CompanyCertificationService;
import com.tradepass.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api")
public class CompanyController {
    private final CompanyService companyService;
    private final CompanyCertificationService certificationService;

    @Autowired
    public CompanyController(CompanyService companyService, CompanyCertificationService certificationService) {
        this.companyService = companyService;
        this.certificationService = certificationService;
    }

    CompanyController(CompanyService companyService) {
        this(companyService, null);
    }

    @GetMapping("/companies/search")
    public ApiResponse<List<CompanySearchSummary>> searchCompanies(@RequestParam String keyword) {
        return ApiResponse.ok(companyService.searchCompanies(keyword));
    }

    @GetMapping("/companies/{id}")
    public ApiResponse<CompanyProfile> getCompany(@PathVariable String id) {
        return ApiResponse.ok(companyService.getCompany(id));
    }

    @PostMapping("/companies")
    public ApiResponse<CompanyProfile> submitCompany(@Valid @RequestBody CompanySubmitRequest request) {
        return ApiResponse.ok(companyService.submitCompany(request));
    }

    @PostMapping("/companies/{id}/certifications")
    public ApiResponse<CertificationApplicationPayload> submitCertification(@PathVariable String id) {
        if (certificationService == null) {
            throw new IllegalStateException("企业认证服务未配置");
        }
        return ApiResponse.ok(certificationService.submit(id));
    }

    @PostMapping("/verifications/real-name")
    public ApiResponse<CompanyProfile> verifyRealName(@Valid @RequestBody VerificationRequest request) {
        return ApiResponse.ok(companyService.verifyRealName(request));
    }

    @PostMapping("/verifications/face")
    public ApiResponse<CompanyProfile> verifyFace(@Valid @RequestBody VerificationRequest request) {
        return ApiResponse.ok(companyService.verifyFace(request));
    }

    @PostMapping("/seals")
    public ApiResponse<SealRecord> uploadSeal(@Valid @RequestBody SealRequest request) {
        return ApiResponse.ok(companyService.uploadSeal(request));
    }

    @PostMapping("/companies/invite")
    public ApiResponse<InviteResult> createInvite(@Valid @RequestBody InviteRequest request) {
        return ApiResponse.ok(companyService.createInvite(request));
    }

    @PostMapping("/companies/counterparty-invite")
    public ApiResponse<InviteResult> createCounterpartyInvite(@Valid @RequestBody InviteRequest request) {
        return ApiResponse.ok(companyService.createCounterpartyInvite(request));
    }

    @PostMapping("/companies/join")
    public ApiResponse<JoinResult> joinCompany(@Valid @RequestBody JoinRequest request) {
        return ApiResponse.ok(companyService.joinCompany(request));
    }

    @GetMapping("/authorizations")
    public ApiResponse<PagePayload<AuthorizationRecord>> listMembers(@RequestParam String companyId,
                                                                     @RequestParam(required = false) String status,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(companyService.pageMembers(companyId, status, page, size));
    }

    @PostMapping("/authorizations/{id}/approve")
    public ApiResponse<AuthorizationRecord> approveMember(@PathVariable String id,
                                                          @Valid @RequestBody ApproveRequest request,
                                                          @RequestParam String companyId) {
        return ApiResponse.ok(companyService.approveMember(id, request, companyId));
    }

    @PostMapping("/authorizations/{id}/reject")
    public ApiResponse<Void> rejectMember(@PathVariable String id, @RequestParam String companyId) {
        companyService.rejectMember(id, companyId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/authorizations/{id}")
    public ApiResponse<Void> removeMember(@PathVariable String id, @RequestParam String companyId) {
        companyService.removeMember(id, companyId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<RolePayload>> listRoles(@RequestParam String companyId) {
        return ApiResponse.ok(companyService.listRoles(companyId));
    }

    @PostMapping("/roles")
    public ApiResponse<RolePayload> createRole(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(companyService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<Void> updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        companyService.updateRole(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable String id) {
        companyService.deleteRole(id);
        return ApiResponse.ok(null);
    }
}
