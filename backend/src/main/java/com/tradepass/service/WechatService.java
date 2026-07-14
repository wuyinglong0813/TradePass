package com.tradepass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WechatService {
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${wechat.app-id}")
    private String wechatAppId;

    @Value("${wechat.app-secret}")
    private String wechatAppSecret;

    public String resolveOpenid(String code) {
        if (code.startsWith("dev-")) {
            return code;
        }
        try {
            String url = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    wechatAppId, wechatAppSecret, code);
            var client = java.net.http.HttpClient.newHttpClient();
            var httpReq = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            var resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            var node = mapper.readTree(resp.body());
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                throw new BusinessException("微信登录失败: " + node.get("errmsg").asText());
            }
            return node.get("openid").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("微信登录异常: " + e.getMessage());
        }
    }

    public String resolvePhoneByCode(String phoneCode) {
        try {
            String token = getAccessToken();
            String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + token;
            String body = mapper.writeValueAsString(Map.of("code", phoneCode));
            var client = java.net.http.HttpClient.newHttpClient();
            var httpReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            var node = mapper.readTree(resp.body());
            if (node.path("errcode").asInt(-1) != 0) {
                throw new BusinessException("获取手机号失败: " + node.path("errmsg").asText());
            }
            return node.path("phone_info").path("phoneNumber").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取手机号异常: " + e.getMessage());
        }
    }

    private String getAccessToken() {
        try {
            if (wechatAppSecret == null || wechatAppSecret.isBlank()) {
                throw new BusinessException("未配置 WECHAT_APP_SECRET，无法获取 access_token");
            }
            String url = String.format(
                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                    wechatAppId, wechatAppSecret);
            var client = java.net.http.HttpClient.newHttpClient();
            var httpReq = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            var resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            var node = mapper.readTree(resp.body());
            if (!node.has("access_token")) {
                throw new BusinessException("获取 access_token 失败: " + resp.body());
            }
            return node.get("access_token").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取 access_token 异常: " + e.getMessage());
        }
    }
}
