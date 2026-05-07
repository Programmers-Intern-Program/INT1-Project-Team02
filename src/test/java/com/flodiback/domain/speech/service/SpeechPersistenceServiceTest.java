package com.flodiback.domain.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
import com.flodiback.domain.speech.dto.SavedSpeechResult;
import com.flodiback.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class SpeechPersistenceServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SpeechPersistenceService speechPersistenceService;

    @Test
    void saveUtterance_savesTokenCountAndPublishesEvent() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getId()).willReturn(1L);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        Utterance savedUtterance = mock(Utterance.class);
        given(savedUtterance.getId()).willReturn(100L);
        given(utteranceRepository.save(any(Utterance.class))).willReturn(savedUtterance);

        SavedSpeechResult result = speechPersistenceService.saveUtterance(request("12345678901234567890"));

        assertThat(result.utteranceId()).isEqualTo(100L);
        assertThat(result.meetingId()).isEqualTo(1L);

        ArgumentCaptor<Utterance> utteranceCaptor = ArgumentCaptor.forClass(Utterance.class);
        verify(utteranceRepository).save(utteranceCaptor.capture());
        Utterance utterance = utteranceCaptor.getValue();
        assertThat(utterance.getMeeting()).isEqualTo(meeting);
        assertThat(utterance.getSpeakerDiscordId()).isEqualTo("discord-1");
        assertThat(utterance.getSpeakerName()).isEqualTo("김철수");
        assertThat(utterance.getContent()).isEqualTo("12345678901234567890");
        assertThat(utterance.getSpeechStartedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 10, 0));
        assertThat(utterance.getSpeechEndedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 10, 0, 5));
        assertThat(utterance.getTokenCount()).isEqualTo(5);

        ArgumentCaptor<UtteranceSavedEvent> eventCaptor = ArgumentCaptor.forClass(UtteranceSavedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().meetingId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().utteranceId()).isEqualTo(100L);
    }

    @Test
    void saveUtterance_setsTokenCountZeroWhenTextIsBlank() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getId()).willReturn(1L);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        Utterance savedUtterance = mock(Utterance.class);
        given(savedUtterance.getId()).willReturn(100L);
        given(utteranceRepository.save(any(Utterance.class))).willReturn(savedUtterance);

        speechPersistenceService.saveUtterance(request(" "));

        ArgumentCaptor<Utterance> utteranceCaptor = ArgumentCaptor.forClass(Utterance.class);
        verify(utteranceRepository).save(utteranceCaptor.capture());
        assertThat(utteranceCaptor.getValue().getTokenCount()).isZero();
    }

    @Test
    void saveUtterance_throwsNotFoundWhenMeetingDoesNotExist() {
        given(meetingRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> speechPersistenceService.saveUtterance(request("hello")))
                .isInstanceOf(ServiceException.class)
                .extracting("resultCode")
                .isEqualTo("404-1");
    }

    private InternalSpeechRequest request(String text) {
        return new InternalSpeechRequest(
                1L,
                "discord-1",
                "김철수",
                text,
                LocalDateTime.of(2026, 5, 3, 10, 0),
                LocalDateTime.of(2026, 5, 3, 10, 0, 5));
    }
}
