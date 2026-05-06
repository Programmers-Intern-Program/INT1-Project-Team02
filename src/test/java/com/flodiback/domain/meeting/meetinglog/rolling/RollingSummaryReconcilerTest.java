package com.flodiback.domain.meeting.meetinglog.rolling;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.global.enums.MeetingStatus;

@ExtendWith(MockitoExtension.class)
class RollingSummaryReconcilerTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private RollingSummaryService rollingSummaryService;

    @InjectMocks
    private RollingSummaryReconciler reconciler;

    @Test
    void reconcile_compressesEachInProgressMeetingId() {
        given(meetingRepository.findIdsByStatus(MeetingStatus.IN_PROGRESS)).willReturn(List.of(1L, 2L));

        reconciler.reconcile();

        verify(rollingSummaryService).compressIfNeeded(1L);
        verify(rollingSummaryService).compressIfNeeded(2L);
    }

    @Test
    void reconcile_doesNotCompressWhenNoInProgressMeetingExists() {
        given(meetingRepository.findIdsByStatus(MeetingStatus.IN_PROGRESS)).willReturn(List.of());

        reconciler.reconcile();

        verify(rollingSummaryService, never()).compressIfNeeded(org.mockito.ArgumentMatchers.any());
    }
}
