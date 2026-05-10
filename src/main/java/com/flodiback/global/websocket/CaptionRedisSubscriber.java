package com.flodiback.global.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.speech.dto.CaptionEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CaptionRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(CaptionRedisSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CaptionEvent event = objectMapper.readValue(message.getBody(), CaptionEvent.class);
            messagingTemplate.convertAndSend("/topic/meetings/" + event.meetingId() + "/captions", event);
        } catch (Exception e) {
            log.warn("caption Redis 메시지 처리 실패: {}", e.getMessage());
        }
    }
}
