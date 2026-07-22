package com.tradepass.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final DevModeInterceptor devModeInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, DevModeInterceptor devModeInterceptor) {
        this.authInterceptor = authInterceptor;
        this.devModeInterceptor = devModeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 生产环境优先拦截 dev 接口（返回 404）
        registry.addInterceptor(devModeInterceptor).addPathPatterns("/api/dev/**");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/wechat-login",   // 登录本身不需要 token
                        "/api/company-certifications/provider-callback", // 外部回调使用独立密钥校验
                        "/api/dev/**"               // dev 接口无需 token（仅 dev.enabled=true 时可访问）
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
