package com.flodiback.bot.stt;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisPublisher(String host, int port) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(8);
        this.jedisPool = new JedisPool(config, host, port);
        log.info("[Redis] publisher 초기화 완료: {}:{}", host, port);
    }

    public void publishPartial(
            long meetingId, String speakerDiscordId, String speakerName, String text, long sequence) {
        String payload =
                buildPayload("caption.partial", meetingId, speakerDiscordId, speakerName, text, false, sequence);
        if (payload == null) {
            return;
        }
        publish("captions:meetings:" + meetingId, payload);
    }

    private void publish(String channel, String payload) {
        try (var jedis = jedisPool.getResource()) {
            jedis.publish(channel, payload);
        } catch (Exception e) {
            log.warn("[Redis] publish 실패: channel={}, {}", channel, e.getMessage());
        }
    }

    private String buildPayload(
            String type,
            long meetingId,
            String speakerDiscordId,
            String speakerName,
            String text,
            boolean isFinal,
            long sequence) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            node.put("meetingId", meetingId);
            node.put("speakerDiscordId", speakerDiscordId);
            node.put("speakerName", speakerName);
            node.put("text", text);
            node.put("isFinal", isFinal);
            node.put("sequence", sequence);
            node.put("sentAt", Instant.now().toString());
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("[Redis] payload 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    public void close() {
        jedisPool.close();
    }
}
