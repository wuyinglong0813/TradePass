package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.DevUser;
import com.tradepass.common.TradePassDtos.LoginSession;
import com.tradepass.common.TradePassDtos.MePayload;
import com.tradepass.common.TradePassDtos.UserProfile;
import com.tradepass.dto.request.BindCompanyRequest;
import com.tradepass.dto.request.BindPhoneRequest;
import com.tradepass.dto.request.SwitchCompanyRequest;
import com.tradepass.dto.request.SwitchUserRequest;
import com.tradepass.dto.request.WechatLoginRequest;
import com.tradepass.dto.response.TodoItem;
import com.tradepass.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/wechat-login")
    public ApiResponse<LoginSession> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.ok(authService.wechatLogin(request));
    }

    @PostMapping("/auth/bind-phone")
    public ApiResponse<UserProfile> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        return ApiResponse.ok(authService.bindPhone(request));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<MePayload> me() {
        return ApiResponse.ok(authService.me());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.ok(authService.permissions());
    }

    @GetMapping("/me/todos")
    public ApiResponse<List<TodoItem>> myTodos() {
        return ApiResponse.ok(authService.myTodos());
    }

    @PostMapping("/me/switch-company")
    public ApiResponse<MePayload> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        return ApiResponse.ok(authService.switchCompany(request));
    }

    @PostMapping("/me/company")
    public ApiResponse<MePayload> bindCompany(@Valid @RequestBody BindCompanyRequest request) {
        return ApiResponse.ok(authService.bindCompany(request));
    }

    @GetMapping("/dev/users")
    public ApiResponse<List<DevUser>> listDevUsers() {
        return ApiResponse.ok(authService.listDevUsers());
    }

    @PostMapping("/dev/switch-user")
    public ApiResponse<LoginSession> switchUser(@Valid @RequestBody SwitchUserRequest request) {
        return ApiResponse.ok(authService.switchUser(request));
    }
}
