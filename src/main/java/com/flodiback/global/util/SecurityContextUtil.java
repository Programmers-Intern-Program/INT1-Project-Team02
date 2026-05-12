package com.flodiback.global.util;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    public static String getUserId() {
        return (String) getAuthentication().getPrincipal();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getGuildIds() {
        return (List<String>) getAuthentication().getDetails();
    }

    private static Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }
        return auth;
    }
}
