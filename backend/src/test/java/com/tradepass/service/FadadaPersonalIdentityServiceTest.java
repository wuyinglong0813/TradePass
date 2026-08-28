package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.config.FadadaProperties;
import com.tradepass.dto.response.FadadaAuthUrlPayload;
import com.tradepass.dto.response.PersonalIdentityPayload;
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.entity.FadadaUserIdentity;
import com.tradepass.entity.SysUser;
import com.tradepass.integration.fadada.FadadaUserGateway;
import com.tradepass.mapper.FadadaCallbackEventMapper;
import com.tradepass.mapper.FadadaUserIdentityMapper;
import com.tradepass.mapper.SysUserMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FadadaPersonalIdentityServiceTest {
    private FadadaUserIdentityMapper identityMapper;
    private FadadaCallbackEventMapper callbackEventMapper;
    private SysUserMapper userMapper;
    private FadadaUserGateway gateway;
    private FadadaProperties properties;
    private FadadaPersonalIdentityService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(FadadaUserIdentity.class, FadadaCallbackEvent.class, SysUser.class);
        identityMapper = mock(FadadaUserIdentityMapper.class);
        callbackEventMapper = mock(FadadaCallbackEventMapper.class);
        userMapper = mock(SysUserMapper.class);
        gateway = mock(FadadaUserGateway.class);
        properties = enabledProperties();
        service = new FadadaPersonalIdentityService(identityMapper, callbackEventMapper,
                userMapper, gateway, properties, new ObjectMapper());
        AuthContext.set(8L, null);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void createsPersonalAuthUrlWithoutCollectingIdentityDocumentData() {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setPhone("13800000000");
        when(userMapper.selectById(8L)).thenReturn(user);
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            FadadaUserIdentity identity = invocation.getArgument(0);
            identity.setId(18L);
            return 1;
        }).when(identityMapper).insert(any(FadadaUserIdentity.class));
        when(gateway.createAuthUrl(any())).thenReturn(
                new FadadaUserGateway.AuthUrlResult("https://auth.fadada.com/personal/8"));

        FadadaAuthUrlPayload result = service.createAuthUrl();

        assertThat(result.authUrl()).isEqualTo("https://auth.fadada.com/personal/8");
        assertThat(result.identity().status()).isEqualTo("IN_PROGRESS");
        ArgumentCaptor<FadadaUserGateway.AuthUrlCommand> command =
                ArgumentCaptor.forClass(FadadaUserGateway.AuthUrlCommand.class);
        verify(gateway).createAuthUrl(command.capture());
        assertThat(command.getValue().clientUserId()).isEqualTo("tradepass-user-8");
        assertThat(command.getValue().accountName()).isEqualTo("13800000000");
        assertThat(command.getValue().callbackUrl()).contains("token=callback-secret");
    }

    @Test
    void allowsLocalAuthUrlVerificationBeforePublicCallbackIsConfigured() {
        properties.setCallbackUrl("");
        properties.setCallbackToken("");
        SysUser user = new SysUser();
        user.setId(8L);
        user.setPhone("13800000000");
        when(userMapper.selectById(8L)).thenReturn(user);
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            FadadaUserIdentity identity = invocation.getArgument(0);
            identity.setId(18L);
            return 1;
        }).when(identityMapper).insert(any(FadadaUserIdentity.class));
        when(gateway.createAuthUrl(any())).thenReturn(
                new FadadaUserGateway.AuthUrlResult("https://auth.fadada.com/personal/8"));

        service.createAuthUrl();

        ArgumentCaptor<FadadaUserGateway.AuthUrlCommand> command =
                ArgumentCaptor.forClass(FadadaUserGateway.AuthUrlCommand.class);
        verify(gateway).createAuthUrl(command.capture());
        assertThat(command.getValue().callbackUrl()).isNull();
    }

    @Test
    void synchronizesAuthoritativeProviderStatusAndVerifiedName() {
        FadadaUserIdentity identity = identity("IN_PROGRESS");
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(identity);
        when(gateway.getUser("tradepass-user-8", null)).thenReturn(
                new FadadaUserGateway.UserAccountResult("tradepass-user-8", "open-user-8",
                        "authorized", "identified", List.of("ident_info")));
        when(gateway.getIdentityInfo("open-user-8")).thenReturn(
                new FadadaUserGateway.UserIdentityResult("open-user-8", "identified", "张三",
                        "face", "2026-08-27 10:00:00", "2026-08-27 10:05:00"));

        PersonalIdentityPayload result = service.syncCurrent();

        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.verifiedName()).isEqualTo("张三");
        assertThat(result.failureReason()).isNull();
        assertThat(identity.getOpenUserId()).isEqualTo("open-user-8");
        verify(identityMapper).updateById(identity);
    }

    @Test
    void protectsCallbackAndProcessesDuplicateEventOnlyOnce() throws Exception {
        FadadaUserIdentity identity = identity("IN_PROGRESS");
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(identity);
        when(gateway.getUser("tradepass-user-8", "open-user-8")).thenReturn(
                new FadadaUserGateway.UserAccountResult("tradepass-user-8", "open-user-8",
                        "authorized", "identified", List.of("ident_info")));
        when(gateway.getIdentityInfo("open-user-8")).thenReturn(
                new FadadaUserGateway.UserIdentityResult("open-user-8", "identified", "张三",
                        "face", null, "2026-08-27 10:05:00"));
        AtomicReference<FadadaCallbackEvent> savedEvent = new AtomicReference<>();
        when(callbackEventMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> savedEvent.get());
        doAnswer(invocation -> {
            FadadaCallbackEvent event = invocation.getArgument(0);
            event.setId(28L);
            savedEvent.set(event);
            return 1;
        }).when(callbackEventMapper).insert(any(FadadaCallbackEvent.class));
        var payload = new ObjectMapper().readTree("""
                {"eventId":"evt-8","eventType":"user-authorize","data":{
                  "clientUserId":"tradepass-user-8","openUserId":"open-user-8",
                  "authResult":"success","identProcessStatus":"success"
                }}
                """);

        assertThatThrownBy(() -> service.handleCallback("wrong", payload))
                .isInstanceOf(BusinessException.class).hasMessage("法大大回调凭证无效");
        service.handleCallback("callback-secret", payload);
        service.handleCallback("callback-secret", payload);

        assertThat(savedEvent.get().getStatus()).isEqualTo("PROCESSED");
        verify(gateway, times(1)).getUser("tradepass-user-8", "open-user-8");
    }

    private FadadaUserIdentity identity(String localStatus) {
        FadadaUserIdentity identity = new FadadaUserIdentity();
        identity.setId(18L);
        identity.setUserId(8L);
        identity.setClientUserId("tradepass-user-8");
        identity.setLocalStatus(localStatus);
        identity.setBindingStatus("unauthorized");
        identity.setIdentStatus("unidentified");
        identity.setIdentProcessStatus("identifying");
        identity.setAuthScopes("[\"ident_info\"]");
        return identity;
    }

    private FadadaProperties enabledProperties() {
        FadadaProperties value = new FadadaProperties();
        value.setEnabled(true);
        value.setAppId("app-id");
        value.setAppSecret("app-secret");
        value.setServerUrl("https://api.fadada.com/api/v5");
        value.setCallbackUrl("https://tradepass.example.com/api/fadada/callback");
        value.setCallbackToken("callback-secret");
        value.setRedirectMiniAppUrl("/pages/personal-cert/personal-cert");
        return value;
    }
}
