package com.flodiback.global.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

    private static final String CLAIM_GUILD_IDS = "guildIds";

    private final SecretKey secretKey;
    private final long expireHours;

    public JwtProvider(@Value("${jwt.secret}") String secret, @Value("${jwt.expire-hours:24}") long expireHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String issue(String userId, List<String> guildIds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofHours(expireHours).toMillis());

        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_GUILD_IDS, guildIds)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> extractGuildIds(Claims claims) {
        return (List<String>) claims.get(CLAIM_GUILD_IDS, List.class);
    }
}
