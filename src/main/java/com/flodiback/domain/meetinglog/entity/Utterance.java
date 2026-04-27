package com.flodiback.domain.meetinglog.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.flodiback.domain.meeting.entity.Meeting;

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

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "spoken_at", nullable = false, updatable = false)
    private LocalDateTime spokenAt;

    @Builder
    public Utterance(Meeting meeting, String speakerName, String speakerDiscordId, String content) {
        this.meeting = meeting;
        this.speakerName = speakerName;
        this.speakerDiscordId = speakerDiscordId;
        this.content = content;
    }
}
