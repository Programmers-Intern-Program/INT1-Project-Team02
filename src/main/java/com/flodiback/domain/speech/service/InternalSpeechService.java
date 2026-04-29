package com.flodiback.domain.speech.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.InternalSpeechResponse;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InternalSpeechService {

    private final MeetingRepository meetingRepository;
    private final UtteranceRepository utteranceRepository;

    @Transactional
    public InternalSpeechResponse saveSpeech(InternalSpeechRequest request) {
        // 발화는 반드시 기존 회의에 연결되어야 하므로 회의를 먼저 조회한다.
        Meeting meeting = meetingRepository
                .findById(request.meetingId())
                .orElseThrow(() -> new ServiceException("404-1", "회의를 찾을 수 없습니다."));

        // 동시 요청이 들어오면 같은 sequenceNo가 배정될 수 있다.
        // 현재는 단일 봇 환경이므로 허용하지만, 멀티 봇/고부하 환경에서는 DB 시퀀스나 낙관적 락으로 교체해야 한다.
        long sequenceNo = utteranceRepository.countByMeeting(meeting) + 1;

        // Discord 봇이 넘긴 발화 시각을 spoken_at으로 저장한다.
        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerDiscordId(request.speakerDiscordId())
                .speakerName(request.speakerName())
                .content(request.text())
                .spokenAt(request.timestamp())
                .sequenceNo(sequenceNo)
                .build();

        Utterance savedUtterance = utteranceRepository.save(utterance);

        // TODO: 호출어("AI야", "봇아", "클로드야") 감지 후 GLM AI Gateway 스트리밍 응답 흐름을 연결한다.
        // 현재 1차 범위에서는 STT 발화 저장까지만 처리한다.

        return new InternalSpeechResponse(savedUtterance.getId(), meeting.getId());
    }
}
