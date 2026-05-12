package com.flodiback.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flodiback.global.filter.InternalApiKeyFilter;

@Configuration
public class InternalApiKeyFilterConfig {

    @Value("${internal.api-key}")
    private String apiKey;

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("INTERNAL_API_KEY가 설정되지 않았습니다. 환경변수를 확인해주세요.");
        }
        FilterRegistrationBean<InternalApiKeyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiKeyFilter(apiKey));
        bean.addUrlPatterns("/internal/*");
        bean.setOrder(1);
        return bean;
    }
}
