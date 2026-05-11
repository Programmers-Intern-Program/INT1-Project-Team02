package com.flodiback.global.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.global.rsData.RsData;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 내부 API 키 검증 필터.
 *
 * <p>설정된 키가 없으면 검증을 스킵한다 (로컬 개발 편의 및 웹 확장 대비).
 * 키가 설정된 경우 X-Internal-Api-Key 헤더가 일치하지 않으면 403을 반환한다.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalApiKeyFilter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (apiKey == null || apiKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER_NAME);
        if (!apiKey.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(RsData.of("403-1", "접근이 거부되었습니다.")));
            return;
        }

        chain.doFilter(request, response);
    }
}
