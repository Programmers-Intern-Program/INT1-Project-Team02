package com.flodiback.domain.meeting.meetinglog.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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

    @Column(name = "speech_started_at", nullable = false)
    private LocalDateTime speechStartedAt;

    @Column(name = "speech_ended_at")
    private LocalDateTime speechEndedAt;

    @Column(name = "token_count")
    private Integer tokenCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Utterance(
            Meeting meeting,
            String speakerName,
            String speakerDiscordId,
            String speakerType,
            String content,
            LocalDateTime speechStartedAt,
            LocalDateTime speechEndedAt,
            Integer tokenCount,
            LocalDateTime createdAt) {
        this.meeting = meeting;
        this.speakerName = speakerName;
        this.speakerDiscordId = speakerDiscordId;
        this.speakerType = speakerType != null ? speakerType : "HUMAN";
        this.content = content;
        this.speechStartedAt = speechStartedAt != null ? speechStartedAt : LocalDateTime.now();
        this.speechEndedAt = speechEndedAt;
        this.tokenCount = tokenCount;
        this.createdAt = createdAt;
    }
}
