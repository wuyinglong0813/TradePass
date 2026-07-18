package com.tradepass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class WechatService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String wechatAppId;
    private final String wechatAppSecret;
    private final boolean devEnabled;
    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public WechatService(@Value("${wechat.app-id}") String wechatAppId,
                         @Value("${wechat.app-secret}") String wechatAppSecret,
                         @Value("${tradepass.dev.enabled:false}") boolean devEnabled) {
        this.wechatAppId = wechatAppId;
        this.wechatAppSecret = wechatAppSecret;
        this.devEnabled = devEnabled;
    }

    public String resolveOpenid(String code) {
        if (code.startsWith("dev-")) {
            if (!devEnabled) throw new BusinessException("开发登录凭证在当前环境不可用");
            return code;
        }
        requireWechatSecret("无法完成微信登录");
        try {
            String query = "appid=" + encode(wechatAppId)
                    + "&secret=" + encode(wechatAppSecret)
                    + "&js_code=" + encode(code)
                    + "&grant_type=authorization_code";
            JsonNode node = sendJson(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.weixin.qq.com/sns/jscode2session?" + query))
                    .timeout(REQUEST_TIMEOUT)
                    .GET().build());
            if (node.path("errcode").asInt(0) != 0) {
                throw new BusinessException("微信登录失败: " + node.path("errmsg").asText("未知错误"));
            }
            String openid = node.path("openid").asText("");
            if (openid.isBlank()) throw new BusinessException("微信登录失败: 响应缺少 openid");
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("微信登录服务暂时不可用");
        }
    }

    public String resolvePhoneByCode(String phoneCode) {
        try {
            String token = getAccessToken();
            String body = mapper.writeValueAsString(Map.of("code", phoneCode));
            JsonNode node = sendJson(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + encode(token)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build());
            if (node.path("errcode").asInt(-1) != 0) {
                throw new BusinessException("获取手机号失败: " + node.path("errmsg").asText("未知错误"));
            }
            String phone = node.path("phone_info").path("phoneNumber").asText("");
            if (phone.isBlank()) throw new BusinessException("获取手机号失败: 响应缺少手机号");
            return phone;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("微信手机号服务暂时不可用");
        }
    }

    private String getAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(accessTokenExpiresAt)) return cachedAccessToken;
        synchronized (this) {
            now = Instant.now();
            if (cachedAccessToken != null && now.isBefore(accessTokenExpiresAt)) return cachedAccessToken;
            requireWechatSecret("无法获取 access_token");
            try {
                String query = "grant_type=client_credential&appid=" + encode(wechatAppId)
                        + "&secret=" + encode(wechatAppSecret);
                JsonNode node = sendJson(HttpRequest.newBuilder()
                        .uri(URI.create("https://api.weixin.qq.com/cgi-bin/token?" + query))
                        .timeout(REQUEST_TIMEOUT)
                        .GET().build());
                String token = node.path("access_token").asText("");
                if (token.isBlank()) {
                    throw new BusinessException("获取 access_token 失败: " + node.path("errmsg").asText("未知错误"));
                }
                long expiresIn = Math.max(120, node.path("expires_in").asLong(7200));
                cachedAccessToken = token;
                accessTokenExpiresAt = now.plusSeconds(expiresIn - 60);
                return token;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("微信凭证服务暂时不可用");
            }
        }
    }

    private JsonNode sendJson(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("微信服务返回异常状态: " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private void requireWechatSecret(String action) {
        if (wechatAppSecret == null || wechatAppSecret.isBlank()) {
            throw new BusinessException("未配置 WECHAT_APP_SECRET，" + action);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
