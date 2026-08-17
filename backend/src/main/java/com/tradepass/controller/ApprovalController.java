package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.service.ApprovalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/fulfillment")
    public ApiResponse<List<Map<String, Object>>> pendingFulfillment() {
        return ApiResponse.ok(approvalService.pendingFulfillment());
    }
}
