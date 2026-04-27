package com.flodiback.domain.meeting.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "context_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContextCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "compressed_text", nullable = false, columnDefinition = "TEXT")
    private String compressedText;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @Column(name = "turn_range", nullable = false, length = 50)
    private String turnRange;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ContextCache(Meeting meeting, String compressedText, Integer tokenCount, String turnRange) {
        this.meeting = meeting;
        this.compressedText = compressedText;
        this.tokenCount = tokenCount;
        this.turnRange = turnRange;
    }
}
