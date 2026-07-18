package com.tradepass.controller;

import com.tradepass.common.BusinessException;
import com.tradepass.common.GlobalExceptionHandler;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.LoginSession;
import com.tradepass.common.TradePassDtos.UserProfile;
import com.tradepass.dto.response.PagePayload;
import com.tradepass.dto.response.TradeOrderPayload;
import com.tradepass.service.AuthService;
import com.tradepass.service.CompanyService;
import com.tradepass.service.RankingService;
import com.tradepass.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpApiContractTest {
    private AuthService authService;
    private CompanyService companyService;
    private TradeService tradeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        companyService = mock(CompanyService.class);
        tradeService = mock(TradeService.class);
        RankingService rankingService = mock(RankingService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService),
                        new CompanyController(companyService),
                        new TradeController(tradeService),
                        new RankingController(rankingService),
                        new FileController(true))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsInvalidJsonBodyUsingUnifiedApiError() throws Exception {
        mvc.perform(post("/api/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("微信登录 code 不能为空"));
    }

    @Test
    void wrapsSuccessfulLoginAndPublicCompanySearch() throws Exception {
        UserProfile profile = new UserProfile("7", "openid", "", "用户", null, "GUEST");
        when(authService.wechatLogin(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LoginSession("token", profile));
        mvc.perform(post("/api/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wx-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("token"))
                .andExpect(jsonPath("$.data.user.currentRole").value("GUEST"));

        CompanyProfile company = new CompanyProfile("3", "测试企业", "CODE", "法人",
                "VERIFIED", "VERIFIED", "VERIFIED", "UPLOADED");
        when(companyService.searchCompanies("测试")).thenReturn(List.of(company));
        mvc.perform(get("/api/companies/search").param("keyword", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("测试企业"));
    }

    @Test
    void convertsBusinessExceptionToStable400Response() throws Exception {
        when(tradeService.getTemplate(99L)).thenThrow(new BusinessException("模板不存在"));

        mvc.perform(get("/api/contract-templates/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("模板不存在"));
    }

    @Test
    void validatesAndBuildsUploadCredential() throws Exception {
        mvc.perform(post("/api/files/upload-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"seal\",\"fileName\":\"seal.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey").value(org.hamcrest.Matchers.startsWith("tradepass/seal/")))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("seal.png")));

        mvc.perform(post("/api/files/upload-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"\",\"fileName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new FileController(false).uploadToken(new FileController.UploadTokenRequest("seal", "seal.png")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件存储服务尚未配置");
    }

    @Test
    void returnsStablePaginationMetadata() throws Exception {
        TradeOrderPayload order = new TradeOrderPayload("8", "SALE", "客户",
                new BigDecimal("12.50"), LocalDate.of(2026, 7, 1), "CONFIRMED");
        when(tradeService.pageOrders(null, null, 2, 10))
                .thenReturn(PagePayload.of(List.of(order), 21, 2, 10));

        mvc.perform(get("/api/orders").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("8"))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }
}
