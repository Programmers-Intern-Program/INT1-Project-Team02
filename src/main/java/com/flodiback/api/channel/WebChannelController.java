package com.flodiback.api.channel;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meeting.dto.ActiveMeetingResponse;
import com.flodiback.domain.meeting.meeting.service.MeetingService;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class WebChannelController {

    private final MeetingService meetingService;

    @GetMapping("/{channelId}/active-meeting")
    public ResponseEntity<RsData<ActiveMeetingResponse>> getActiveMeeting(@PathVariable String channelId) {
        return meetingService
                .getActiveMeeting(channelId)
                .map(meeting -> ResponseEntity.ok(RsData.of("200-1", "진행 중인 회의 조회 성공.", meeting)))
                .orElse(ResponseEntity.noContent().build());
    }
}
