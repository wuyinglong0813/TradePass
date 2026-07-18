package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tradepass.entity.AuthSession;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.AuthSessionMapper;
import com.tradepass.mapper.SysUserMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {
    private AuthSessionMapper sessionMapper;
    private SysUserMapper userMapper;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(AuthSession.class);
        sessionMapper = mock(AuthSessionMapper.class);
        userMapper = mock(SysUserMapper.class);
        service = new AuthSessionService(sessionMapper, userMapper, 12L);
    }

    @Test
    void issuesOpaqueTokenAndPersistsOnlyItsHash() {
        String token = service.issue(42L);

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionMapper).insert(captor.capture());
        AuthSession saved = captor.getValue();
        assertThat(token).hasSize(43);
        assertThat(saved.getTokenHash()).hasSize(64).doesNotContain(token);
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt() == null
                ? java.time.LocalDateTime.now().minusMinutes(1)
                : saved.getCreatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesOnlyActiveUserForValidSession() {
        AuthSession session = new AuthSession();
        session.setUserId(42L);
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);
        SysUser user = new SysUser();
        user.setId(42L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(42L)).thenReturn(user);

        assertThat(service.resolveUserId("Bearer abc")).isEqualTo(42L);

        user.setStatus("DISABLED");
        assertThat(service.resolveUserId("abc")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesMissingInvalidAndRevokedSessions() {
        assertThat(service.resolveUserId(null)).isNull();
        assertThat(service.resolveUserId("Bearer ")).isNull();
        verify(sessionMapper, never()).selectOne(any(Wrapper.class));

        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(service.resolveUserId("expired")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokesBearerTokenAndIgnoresBlankAuthorization() {
        service.revoke("Bearer abc");
        verify(sessionMapper).update(any(Wrapper.class));

        service.revoke(" ");
        verify(sessionMapper).update(any(Wrapper.class));
    }
}
