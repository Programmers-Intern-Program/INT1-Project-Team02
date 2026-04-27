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
@Table(name = "meeting_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private Meeting meeting;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "unresolved_items", columnDefinition = "TEXT")
    private String unresolvedItems;

    // boolean confirmed → Lombok이 isConfirmed() 생성
    @Column(name = "is_confirmed", nullable = false)
    private boolean confirmed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MeetingSummary(Meeting meeting, String summary, String unresolvedItems) {
        this.meeting = meeting;
        this.summary = summary;
        this.unresolvedItems = unresolvedItems;
        this.confirmed = false;
    }
}
