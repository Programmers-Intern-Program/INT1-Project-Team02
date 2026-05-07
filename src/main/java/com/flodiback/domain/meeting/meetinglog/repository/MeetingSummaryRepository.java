package com.flodiback.domain.meeting.meetinglog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;

public interface MeetingSummaryRepository extends JpaRepository<MeetingSummary, Long> {

    @Query(
            "SELECT ms FROM MeetingSummary ms WHERE ms.meeting.project.id = :projectId AND ms.meeting.id <> :currentMeetingId")
    List<MeetingSummary> findPastByProjectId(
            @Param("projectId") Long projectId, @Param("currentMeetingId") Long currentMeetingId);

    @Query(value = """
                    SELECT ms.*
                    FROM meeting_summaries ms
                    JOIN meetings m ON ms.meeting_id = m.id
                    WHERE m.project_id = :projectId
                      AND ms.meeting_id <> :currentMeetingId
                    ORDER BY ms.created_at DESC
                    LIMIT :topK
                    """, nativeQuery = true)
    List<MeetingSummary> findLatestPastByProjectId(
            @Param("projectId") Long projectId,
            @Param("currentMeetingId") Long currentMeetingId,
            @Param("topK") int topK);

    @Query(value = """
                    SELECT ms.unresolved_items
                    FROM meeting_summaries ms
                    JOIN meetings m ON ms.meeting_id = m.id
                    WHERE m.project_id = :projectId
                      AND ms.meeting_id <> :currentMeetingId
                      AND ms.unresolved_items IS NOT NULL
                      AND btrim(ms.unresolved_items) <> ''
                    ORDER BY ms.created_at DESC
                    LIMIT 1
                    """, nativeQuery = true)
    Optional<String> findLatestUnresolvedItemsByProjectId(
            @Param("projectId") Long projectId, @Param("currentMeetingId") Long currentMeetingId);

    @Modifying
    @Query(
            value = "UPDATE meeting_summaries SET embedding = CAST(:embedding AS vector) WHERE id = :id",
            nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    @Query(value = """
                    WITH semantic AS (
                        SELECT ms.id,
                               1 - (ms.embedding <=> CAST(:embedding AS vector)) AS semantic_score
                        FROM meeting_summaries ms
                        JOIN meetings m ON ms.meeting_id = m.id
                        WHERE m.project_id = :projectId
                          AND ms.meeting_id <> :currentMeetingId
                          AND ms.embedding IS NOT NULL
                    ),
                    combined AS (
                        SELECT s.id,
                               s.semantic_score * :semanticWeight
                               + COALESCE(ts_rank(ms.summary_tsv,
                                   plainto_tsquery('simple', :queryText)), 0) * :keywordWeight
                               AS total_score
                        FROM semantic s
                        JOIN meeting_summaries ms ON s.id = ms.id
                    )
                    SELECT ms.*
                    FROM meeting_summaries ms
                    JOIN combined c ON ms.id = c.id
                    ORDER BY c.total_score DESC
                    LIMIT :topK
                    """, nativeQuery = true)
    List<MeetingSummary> hybridSearch(
            @Param("projectId") Long projectId,
            @Param("currentMeetingId") Long currentMeetingId,
            @Param("embedding") String embedding,
            @Param("queryText") String queryText,
            @Param("topK") int topK,
            @Param("semanticWeight") double semanticWeight,
            @Param("keywordWeight") double keywordWeight);
}
