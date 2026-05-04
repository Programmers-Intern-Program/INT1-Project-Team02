package com.flodiback.domain.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.rolling.UtteranceSavedEvent;
import com.flodiback.domain.speech.dto.InternalSpeechRequest;

@ExtendWith(MockitoExtension.class)
class InternalSpeechServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private SpeechAiAnswerService speechAiAnswerService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InternalSpeechService internalSpeechService;

    @Test
    void saveSpeech_발화저장시_tokenCount와_event를_생성() {
        Meeting meeting = org.mockito.Mockito.mock(Meeting.class);
        given(meeting.getId()).willReturn(1L);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.countByMeeting(meeting)).willReturn(0L);
        Utterance savedUtterance = org.mockito.Mockito.mock(Utterance.class);
        given(savedUtterance.getId()).willReturn(100L);
        given(utteranceRepository.save(any(Utterance.class))).willReturn(savedUtterance);

        InternalSpeechRequest request = new InternalSpeechRequest(
                1L, "discord-1", "김철수", "12345678901234567890", LocalDateTime.of(2026, 5, 3, 10, 0));

        internalSpeechService.saveSpeech(request);

        ArgumentCaptor<Utterance> utteranceCaptor = ArgumentCaptor.forClass(Utterance.class);
        verify(utteranceRepository).save(utteranceCaptor.capture());
        assertThat(utteranceCaptor.getValue().getTokenCount()).isEqualTo(5);

        ArgumentCaptor<UtteranceSavedEvent> eventCaptor = ArgumentCaptor.forClass(UtteranceSavedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().meetingId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().utteranceId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().sequenceNo()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().tokenCount()).isEqualTo(5);
    }

    @Test
    void saveSpeech_blankText는_tokenCount_0() {
        Meeting meeting = org.mockito.Mockito.mock(Meeting.class);
        given(meeting.getId()).willReturn(1L);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        Utterance savedUtterance = org.mockito.Mockito.mock(Utterance.class);
        given(savedUtterance.getId()).willReturn(100L);
        given(utteranceRepository.save(any(Utterance.class))).willReturn(savedUtterance);

        InternalSpeechRequest request =
                new InternalSpeechRequest(1L, "discord-1", "김철수", " ", LocalDateTime.of(2026, 5, 3, 10, 0));

        internalSpeechService.saveSpeech(request);

        ArgumentCaptor<Utterance> utteranceCaptor = ArgumentCaptor.forClass(Utterance.class);
        verify(utteranceRepository).save(utteranceCaptor.capture());
        assertThat(utteranceCaptor.getValue().getTokenCount()).isZero();
    }
}
