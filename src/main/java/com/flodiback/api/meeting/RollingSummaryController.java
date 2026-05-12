package com.flodiback.api.meeting;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryPersistenceService;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryPersistenceService.RollingSummarySnapshot;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/meetings")
@RequiredArgsConstructor
public class RollingSummaryController {

    private final RollingSummaryPersistenceService rollingSummaryPersistenceService;

    @GetMapping("/{id}/rolling-summary")
    public ResponseEntity<RsData<RollingSummarySnapshot>> getLatestSummary(@PathVariable Long id) {
        return rollingSummaryPersistenceService
                .getLatestSummary(id)
                .map(snapshot -> ResponseEntity.ok(RsData.of("200-1", "롤링 요약 조회 성공.", snapshot)))
                .orElse(ResponseEntity.noContent().build());
    }
}
