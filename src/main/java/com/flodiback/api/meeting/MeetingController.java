package com.flodiback.api.meeting;

import org.springframework.web.bind.annotation.*;

import com.flodiback.domain.meeting.meeting.dto.CreateMeetingRequest;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.service.MeetingService;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService service;

    @PostMapping
    public RsData<CreateMeetingResponse> createMeeting(@RequestBody CreateMeetingRequest req) {
        return RsData.of("201-1", "회의가 생성되었습니다.", service.create(req));
    }

    @PutMapping("/{id}/end")
    public RsData<MeetingDetailResponse> endMeeting(@PathVariable Long id) {
        return RsData.of("200-1", "회의가 종료되었습니다.", service.end(id));
    }

    @GetMapping("/{id}")
    public RsData<MeetingDetailResponse> getMeeting(@PathVariable Long id) {
        return RsData.of("200-1", "회의 조회 성공.", service.getById(id));
    }
}
