package com.tradepass.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tradepass.fadada")
public class FadadaProperties {
    private boolean enabled;
    private String appId = "";
    private String appSecret = "";
    private String serverUrl = "https://api.fadada.com/api/v5";
    private String callbackUrl = "";
    private String callbackToken = "";
    private String redirectUrl = "";
    private String redirectMiniAppUrl = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = safe(appId); }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = safe(appSecret); }
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = safe(serverUrl); }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = safe(callbackUrl); }
    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = safe(callbackToken); }
    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = safe(redirectUrl); }
    public String getRedirectMiniAppUrl() { return redirectMiniAppUrl; }
    public void setRedirectMiniAppUrl(String redirectMiniAppUrl) { this.redirectMiniAppUrl = safe(redirectMiniAppUrl); }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
