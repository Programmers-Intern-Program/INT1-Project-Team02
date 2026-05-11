package com.flodiback.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flodiback.global.filter.InternalApiKeyFilter;

@Configuration
public class InternalApiKeyFilterConfig {

    @Value("${internal.api-key:}")
    private String apiKey;

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter() {
        FilterRegistrationBean<InternalApiKeyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiKeyFilter(apiKey));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
