package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tradepass.entity.AuthSession;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.AuthSessionMapper;
import com.tradepass.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionMapper authSessionMapper;
    private final SysUserMapper sysUserMapper;
    private final long sessionHours;

    public AuthSessionService(AuthSessionMapper authSessionMapper,
                              SysUserMapper sysUserMapper,
                              @Value("${tradepass.auth.session-hours:168}") long sessionHours) {
        this.authSessionMapper = authSessionMapper;
        this.sysUserMapper = sysUserMapper;
        this.sessionHours = sessionHours;
    }

    public String issue(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AuthSession session = new AuthSession();
        session.setTokenHash(hash(token));
        session.setUserId(userId);
        session.setExpiresAt(LocalDateTime.now().plusHours(sessionHours));
        session.setRevoked(false);
        authSessionMapper.insert(session);
        return token;
    }

    public Long resolveUserId(String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return null;
        }
        AuthSession session = authSessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getTokenHash, hash(token))
                .eq(AuthSession::getRevoked, false)
                .gt(AuthSession::getExpiresAt, LocalDateTime.now())
                .last("LIMIT 1"));
        if (session == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(session.getUserId());
        return user != null && "ACTIVE".equals(user.getStatus()) ? user.getId() : null;
    }

    public void revoke(String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return;
        }
        authSessionMapper.update(new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getTokenHash, hash(token))
                .set(AuthSession::getRevoked, true));
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return token.isBlank() ? null : token;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
