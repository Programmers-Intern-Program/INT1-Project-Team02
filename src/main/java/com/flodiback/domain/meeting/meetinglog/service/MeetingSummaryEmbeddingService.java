package com.flodiback.domain.meeting.meetinglog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.global.embedding.OpenAiEmbeddingClient;
import com.pgvector.PGvector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingSummaryEmbeddingService {

    private final OpenAiEmbeddingClient embeddingClient;
    private final MeetingSummaryRepository meetingSummaryRepository;

    @Transactional
    public void processEmbedding(MeetingSummary meetingSummary) {
        if (meetingSummary == null) return;

        float[] raw;
        try {
            raw = embeddingClient.embed(meetingSummary.getSummary());
        } catch (Exception e) {
            log.warn(
                    "Meeting summary embedding API call failed - summaryId={}: {}",
                    meetingSummary.getId(),
                    e.getMessage());
            return;
        }

        if (raw == null || raw.length == 0) return;

        String embeddingStr = new PGvector(raw).getValue();
        meetingSummaryRepository.updateEmbedding(meetingSummary.getId(), embeddingStr);
    }
}
