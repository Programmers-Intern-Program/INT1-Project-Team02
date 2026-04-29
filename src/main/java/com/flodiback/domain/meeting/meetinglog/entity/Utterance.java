package com.flodiback.domain.meeting.meetinglog.entity;

import java.time.LocalDateTime;

import com.flodiback.domain.meeting.meeting.entity.Meeting;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "utterances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utterance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "speaker_name", nullable = false, length = 100)
    private String speakerName;

    @Column(name = "speaker_discord_id", nullable = false, length = 50)
    private String speakerDiscordId;

    @Column(name = "speaker_type", nullable = false, length = 20)
    private String speakerType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "spoken_at", nullable = false)
    private LocalDateTime spokenAt;

    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Builder
    public Utterance(
            Meeting meeting,
            String speakerName,
            String speakerDiscordId,
            String speakerType,
            String content,
            LocalDateTime spokenAt,
            Long sequenceNo,
            Integer tokenCount) {
        this.meeting = meeting;
        this.speakerName = speakerName;
        this.speakerDiscordId = speakerDiscordId;
        this.speakerType = speakerType != null ? speakerType : "HUMAN";
        this.content = content;
        this.spokenAt = spokenAt != null ? spokenAt : LocalDateTime.now();
        this.sequenceNo = sequenceNo;
        this.tokenCount = tokenCount;
    }
}
