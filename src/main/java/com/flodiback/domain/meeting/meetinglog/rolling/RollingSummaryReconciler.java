package com.flodiback.domain.meeting.meetinglog.rolling;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.global.enums.MeetingStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollingSummaryReconciler {

    private final MeetingRepository meetingRepository;
    private final RollingSummaryService rollingSummaryService;

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        List<Long> activeMeetingIds = meetingRepository.findIdsByStatus(MeetingStatus.IN_PROGRESS);
        for (Long meetingId : activeMeetingIds) {
            rollingSummaryService.compressIfNeeded(meetingId);
        }
    }
}
