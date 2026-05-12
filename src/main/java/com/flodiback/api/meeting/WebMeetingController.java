package com.flodiback.api.meeting;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.service.MeetingService;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryPersistenceService;
import com.flodiback.domain.meeting.meetinglog.rolling.RollingSummaryPersistenceService.RollingSummarySnapshot;
import com.flodiback.global.rsData.RsData;
import com.flodiback.global.util.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class WebMeetingController {

    private final MeetingService meetingService;
    private final RollingSummaryPersistenceService rollingSummaryPersistenceService;

    @GetMapping("/{id}")
    public ResponseEntity<RsData<MeetingDetailResponse>> getMeeting(@PathVariable Long id) {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        MeetingDetailResponse meeting = meetingService.getByIdForUser(id, guildIds);
        return ResponseEntity.ok(RsData.of("200-1", "회의 조회 성공.", meeting));
    }

    @GetMapping("/{id}/rolling-summary")
    public ResponseEntity<RsData<RollingSummarySnapshot>> getRollingSummary(@PathVariable Long id) {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        meetingService.getByIdForUser(id, guildIds);
        return rollingSummaryPersistenceService
                .getLatestSummary(id)
                .map(snapshot -> ResponseEntity.ok(RsData.of("200-1", "롤링 요약 조회 성공.", snapshot)))
                .orElse(ResponseEntity.noContent().build());
    }
}
