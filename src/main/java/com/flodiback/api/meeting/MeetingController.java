package com.flodiback.api.meeting;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.flodiback.domain.meeting.meeting.dto.CreateMeetingRequest;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.service.MeetingService;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService service;

    @PostMapping
    public RsData<CreateMeetingResponse> createMeeting(@RequestBody CreateMeetingRequest req) {
        return RsData.of("201-1", "회의가 생성되었습니다.", service.create(req));
    }

    @GetMapping
    public RsData<List<MeetingDetailResponse>> getMeetings(
            @RequestParam Long projectId, @RequestHeader(name = "X-Guild-Id", required = false) String guildId) {
        return RsData.of("200-1", "회의 목록 조회 성공.", service.getByProjectId(projectId, guildId));
    }

    @PutMapping("/{id}/end")
    public RsData<MeetingDetailResponse> endMeeting(
            @PathVariable Long id, @RequestHeader(name = "X-Requester-Id", required = false) String requesterId) {
        return RsData.of("200-1", "회의가 종료되었습니다.", service.end(id, requesterId));
    }

    @GetMapping("/{id}")
    public RsData<MeetingDetailResponse> getMeeting(
            @PathVariable Long id, @RequestHeader(name = "X-Guild-Id", required = false) String guildId) {
        return RsData.of("200-1", "회의 조회 성공.", service.getById(id, guildId));
    }

    @DeleteMapping("/{id}")
    public RsData<Void> deleteMeeting(
            @PathVariable Long id, @RequestHeader(name = "X-Requester-Id", required = false) String requesterId) {
        service.delete(id, requesterId);
        return RsData.of("200-1", "회의가 삭제되었습니다.");
    }
}
