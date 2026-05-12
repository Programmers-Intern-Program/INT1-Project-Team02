package com.flodiback.global.embedding;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class OpenAiEmbeddingClient {

    private static final String MODEL = "text-embedding-3-small";

    private final RestClient restClient;

    public OpenAiEmbeddingClient(@Value("${openai.embedding.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public float[] embed(String text) {
        EmbeddingResponse response = restClient
                .post()
                .uri("/embeddings")
                .body(Map.of("model", MODEL, "input", text))
                .retrieve()
                .body(EmbeddingResponse.class);
        return response.data().get(0).embedding();
    }

    record EmbeddingResponse(List<EmbeddingData> data) {}

    record EmbeddingData(@JsonProperty("embedding") float[] embedding) {}
}
