package com.flodiback.api.auth;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.auth.DiscordOAuthService;
import com.flodiback.domain.auth.DiscordOAuthService.DiscordUser;
import com.flodiback.global.auth.JwtProvider;
import com.flodiback.global.rsData.RsData;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthController {

    static final String JWT_COOKIE = "jwt";
    private static final String DEFAULT_LOGIN_REDIRECT = "/projects";

    private final DiscordOAuthService discordOAuthService;
    private final JwtProvider jwtProvider;

    @Value("${app.frontend-url}")
    private String frontendOrigin;

    /** Discord OAuth 인증 페이지로 리다이렉트 */
    @GetMapping("/discord")
    public ResponseEntity<Void> redirectToDiscord(@RequestParam(required = false) String redirect) {
        String safeRedirect = safeRedirectPathOrDefault(redirect);
        return ResponseEntity.status(302)
                .location(URI.create(discordOAuthService.buildAuthorizationUrl(safeRedirect)))
                .build();
    }

    /** Discord 콜백: code → JWT 발급 → httpOnly 쿠키 설정 → 프론트로 리다이렉트 */
    @GetMapping("/discord/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam String code, @RequestParam(required = false) String state) {
        String accessToken = discordOAuthService.exchangeToken(code);
        DiscordUser user = discordOAuthService.fetchUser(accessToken);
        List<String> userGuildIds = discordOAuthService.fetchGuildIds(accessToken);
        List<String> botGuildIds = discordOAuthService.filterBotGuilds(userGuildIds);

        if (botGuildIds.isEmpty()) {
            // 봇이 있는 서버에 소속되지 않은 유저는 접근 불가
            return ResponseEntity.status(302)
                    .location(URI.create(frontendOrigin + "/unauthorized"))
                    .build();
        }

        String jwt = jwtProvider.issue(user.id(), botGuildIds);
        ResponseCookie cookie = buildJwtCookie(jwt, Duration.ofHours(24));

        return ResponseEntity.status(302)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(frontendOrigin + safeRedirectPathOrDefault(state)))
                .build();
    }

    /** 로그아웃: JWT 쿠키 만료 */
    @PostMapping("/logout")
    public ResponseEntity<RsData<Void>> logout() {
        ResponseCookie expiredCookie = buildJwtCookie("", Duration.ZERO);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(RsData.of("200-1", "로그아웃되었습니다."));
    }

    /** 현재 로그인 유저 정보 반환 */
    @GetMapping("/me")
    public ResponseEntity<RsData<MeResponse>> me(@CookieValue(name = JWT_COOKIE, required = false) String jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).body(RsData.of("401-1", "로그인이 필요합니다."));
        }
        try {
            Claims claims = jwtProvider.parse(jwt);
            List<String> guildIds = jwtProvider.extractGuildIds(claims);
            return ResponseEntity.ok(RsData.of("200-1", "ok", new MeResponse(claims.getSubject(), guildIds)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(RsData.of("401-1", "유효하지 않은 토큰입니다."));
        }
    }

    private ResponseCookie buildJwtCookie(String value, Duration maxAge) {
        return ResponseCookie.from(JWT_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    static String safeRedirectPathOrDefault(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return DEFAULT_LOGIN_REDIRECT;
        }
        if (redirect.length() > 2048
                || !redirect.startsWith("/")
                || redirect.startsWith("//")
                || redirect.regionMatches(true, 0, "http://", 0, "http://".length())
                || redirect.regionMatches(true, 0, "https://", 0, "https://".length())) {
            return DEFAULT_LOGIN_REDIRECT;
        }
        for (int i = 0; i < redirect.length(); i++) {
            char ch = redirect.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                return DEFAULT_LOGIN_REDIRECT;
            }
        }
        return redirect;
    }

    public record MeResponse(String userId, List<String> guildIds) {}
}
