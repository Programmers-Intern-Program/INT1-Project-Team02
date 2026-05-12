package com.flodiback.global.webMvc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 웹 대시보드 및 OAuth: 쿠키 인증 필요
        addCorsMapping(registry, "/api/v1/**", true);
        addCorsMapping(registry, "/auth/v1/**", true);
        // 봇 내부 API: 쿠키 불필요
        addCorsMapping(registry, "/internal/**", false);
    }

    private void addCorsMapping(CorsRegistry registry, String pattern, boolean allowCredentials) {
        var mapping = registry.addMapping(pattern)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*");
        if (allowCredentials) {
            String[] origins = java.util.Arrays.stream(allowedOrigin.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);
            mapping.allowedOrigins(origins).allowCredentials(true);
        } else {
            mapping.allowedOrigins("*").allowCredentials(false);
        }
    }
}
