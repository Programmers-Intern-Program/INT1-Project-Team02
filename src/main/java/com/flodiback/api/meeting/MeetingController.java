package com.flodiback.api.meeting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.flodiback.api.meeting.dto.CreateMeetingRequest;
import com.flodiback.api.meeting.dto.CreateMeetingResponse;
import com.flodiback.api.meeting.dto.MeetingDetailResponse;
import com.flodiback.application.meeting.MeetingService;
import com.flodiback.application.meeting.result.MeetingResult;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService service;

    @PostMapping
    public ResponseEntity<RsData<CreateMeetingResponse>> createMeeting(@RequestBody CreateMeetingRequest req) {
        MeetingResult result = service.create(req.toCommand());
        CreateMeetingResponse response = CreateMeetingResponse.from(result);
        return ResponseEntity.status(HttpStatus.CREATED).body(RsData.of("201-1", "회의가 생성되었습니다.", response));
    }

    @PutMapping("/{id}/end")
    public ResponseEntity<RsData<MeetingDetailResponse>> endMeeting(@PathVariable Long id) {
        MeetingResult result = service.end(id);
        MeetingDetailResponse response = MeetingDetailResponse.from(result);
        return ResponseEntity.ok(RsData.of("200-1", "회의가 종료되었습니다.", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RsData<MeetingDetailResponse>> getMeeting(@PathVariable Long id) {
        MeetingResult result = service.getById(id);
        MeetingDetailResponse response = MeetingDetailResponse.from(result);
        return ResponseEntity.ok(RsData.of("200-1", "회의 조회 성공.", response));
    }
}
