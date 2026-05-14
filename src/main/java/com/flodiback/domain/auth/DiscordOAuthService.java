package com.flodiback.domain.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.server.server.repository.DiscordServerRepository;

@Service
public class DiscordOAuthService {

    private static final Logger log = LoggerFactory.getLogger(DiscordOAuthService.class);
    private static final String DISCORD_API = "https://discord.com/api/v10";
    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final DiscordServerRepository discordServerRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiscordOAuthService(
            @Value("${discord.oauth.client-id}") String clientId,
            @Value("${discord.oauth.client-secret}") String clientSecret,
            @Value("${discord.oauth.redirect-uri}") String redirectUri,
            DiscordServerRepository discordServerRepository) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.discordServerRepository = discordServerRepository;
    }

    /** Discord OAuth 인증 페이지 URL을 생성한다. */
    public String buildAuthorizationUrl() {
        return buildAuthorizationUrl(null);
    }

    public String buildAuthorizationUrl(String state) {
        String stateParam =
                state == null || state.isBlank() ? "" : "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
        return "https://discord.com/oauth2/authorize"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=identify%20guilds"
                + stateParam;
    }

    /** Authorization code를 access token으로 교환한다. */
    public String exchangeToken(String code) {
        String body = "grant_type=authorization_code"
                + "&code=" + code
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + basicAuth())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[Discord/토큰교환실패] status={}, body={}", response.statusCode(), response.body());
                throw new IllegalStateException("Discord 토큰 교환에 실패했습니다.");
            }

            return objectMapper.readTree(response.body()).path("access_token").asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Discord 토큰 교환 중 오류가 발생했습니다.", e);
        }
    }

    /** access token으로 Discord 유저 정보를 조회한다. */
    public DiscordUser fetchUser(String accessToken) {
        JsonNode data = callDiscordApi(accessToken, "/users/@me");
        return new DiscordUser(data.path("id").asText(), data.path("username").asText());
    }

    /** access token으로 유저가 속한 길드 목록을 조회한다. */
    public List<String> fetchGuildIds(String accessToken) {
        JsonNode guilds = callDiscordApi(accessToken, "/users/@me/guilds");
        List<String> guildIds = new ArrayList<>();
        for (JsonNode guild : guilds) {
            guildIds.add(guild.path("id").asText());
        }
        return guildIds;
    }

    /**
     * 유저의 길드 목록 중 우리 봇이 있는 길드만 필터링한다.
     * discord_servers 테이블에 등록된 guildId 기준으로 교차 확인한다.
     */
    public List<String> filterBotGuilds(List<String> userGuildIds) {
        Set<String> botGuildIds = discordServerRepository.findAll().stream()
                .map(server -> server.getGuildId())
                .collect(Collectors.toSet());

        return userGuildIds.stream().filter(botGuildIds::contains).toList();
    }

    private JsonNode callDiscordApi(String accessToken, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DISCORD_API + path))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[Discord/API호출실패] path={}, status={}", path, response.statusCode());
                throw new IllegalStateException("Discord API 호출에 실패했습니다. path=" + path);
            }

            return objectMapper.readTree(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Discord API 호출 중 오류가 발생했습니다.", e);
        }
    }

    private String basicAuth() {
        String credentials = clientId + ":" + clientSecret;
        return java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public record DiscordUser(String id, String username) {}
}
