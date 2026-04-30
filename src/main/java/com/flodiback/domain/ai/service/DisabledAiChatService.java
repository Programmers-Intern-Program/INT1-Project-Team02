package com.flodiback.domain.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.flodiback.global.exception.ServiceException;

@Service
@ConditionalOnProperty(prefix = "glm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiChatService implements AiChatService {

    @Override
    public String generateAnswer(String systemPrompt, String userQuestion) {
        // 로컬/테스트 환경에서 GLM 키 없이 실행할 수 있도록 명확히 비활성 상태를 알립니다.
        throw new ServiceException("503-1", "GLM 채팅 서비스가 비활성화되어 있습니다.");
    }
}
