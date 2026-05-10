package com.flodiback.api.channel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meeting.dto.ActiveMeetingResponse;
import com.flodiback.domain.meeting.meeting.service.MeetingService;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final MeetingService meetingService;

    @GetMapping("/{channelId}/active-meeting")
    public RsData<ActiveMeetingResponse> getActiveMeeting(@PathVariable String channelId) {
        ActiveMeetingResponse response =
                meetingService.getActiveMeeting(channelId).orElse(null);
        String msg = response != null ? "진행 중인 회의를 찾았습니다." : "진행 중인 회의가 없습니다.";
        return RsData.of("200-1", msg, response);
    }
}
