package com.exam_bank.study_service.feature.analytics.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;

@Repository
public interface StudyAnalyticsRepository extends JpaRepository<StudyReviewEvent, Long> {

    // Group by tag (split comma-separated tagIds) → per-tag correct rate
    @Query(value = """
            WITH expanded AS (
                SELECT
                    CAST(token AS bigint) AS tag_id,
                    sre.is_correct AS is_correct
                FROM study_review_events sre
                CROSS JOIN LATERAL regexp_split_to_table(COALESCE(sre.topic_tag_ids, ''), ',') AS token
                WHERE sre.user_id = :userId
                  AND sre.source = 'EXAM_SUBMISSION'
                  AND token ~ '^[0-9]+$'
            ),
            aggregated AS (
                SELECT
                    e.tag_id AS tag_id,
                    COUNT(*)::int AS total_questions,
                    SUM(CASE WHEN e.is_correct = true THEN 1 ELSE 0 END)::int AS correct_count
                FROM expanded e
                GROUP BY e.tag_id
            )
            SELECT
                a.tag_id AS tagId,
                COALESCE(t.name, CONCAT('Tag #', a.tag_id)) AS tagName,
                a.total_questions AS totalQuestions,
                a.correct_count AS correctCount,
                CASE WHEN a.total_questions = 0 THEN 0
                     ELSE ROUND(
                         CAST(a.correct_count AS numeric)
                         / a.total_questions * 100,
                         2
                     )
                END AS correctRate
            FROM aggregated a
            LEFT JOIN tags t ON t.id = a.tag_id
            ORDER BY correctRate ASC, a.tag_id ASC
            """, nativeQuery = true)
    List<TagStatProjection> findWeaknessByUser(@Param("userId") Long userId);

    // Score history grouped by month
    @Query(value = """
            SELECT
                TO_CHAR(sre.evaluated_at, 'YYYY-MM')  AS period,
                                ROUND(CAST(AVG(sre.score_percent) AS numeric), 2) AS avgScorePercent,
                COUNT(DISTINCT sre.attempt_id)        AS attemptCount,
                                ROUND(CAST(AVG(sre.score_earned) AS numeric), 2) AS avgScoreRaw
            FROM study_review_events sre
            WHERE sre.user_id = :userId
              AND sre.source = 'EXAM_SUBMISSION'
            GROUP BY TO_CHAR(sre.evaluated_at, 'YYYY-MM')
            ORDER BY period DESC
            LIMIT 12
            """, nativeQuery = true)
    List<ScoreHistoryProjection> findScoreHistory(@Param("userId") Long userId);

    @Query(value = """
            SELECT
                    COUNT(*) AS totalQuestions,
                    SUM(CASE WHEN sre.is_correct = true THEN 1 ELSE 0 END) AS correctCount,
                    CASE WHEN COUNT(*) = 0 THEN 0
                             ELSE ROUND(
                                    CAST(SUM(CASE WHEN sre.is_correct = true THEN 1 ELSE 0 END) AS numeric)
                                    / COUNT(*) * 100,
                                    2
                             )
                    END AS correctRate
            FROM study_review_events sre
            WHERE sre.user_id = :userId
                AND sre.source = 'EXAM_SUBMISSION'
            """, nativeQuery = true)
    OverallStatProjection findOverallPerformanceByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT sre.attemptId) FROM StudyReviewEvent sre " +
            "WHERE sre.userId = :userId AND sre.source = 'EXAM_SUBMISSION'")
    int countTotalAttempts(@Param("userId") Long userId);

    @Query("SELECT ROUND(AVG(sre.scorePercent), 2) FROM StudyReviewEvent sre " +
            "WHERE sre.userId = :userId AND sre.source = 'EXAM_SUBMISSION'")
    Double avgScorePercent(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(CAST(ROUND(SUM(COALESCE(sre.latency_ms, 0)) / 60000.0) AS bigint), 0)
            FROM study_review_events sre
            WHERE sre.user_id = :userId
              AND sre.source = 'EXAM_SUBMISSION'
            """, nativeQuery = true)
    Long sumTotalStudyMinutes(@Param("userId") Long userId);

    @Query(value = """
            SELECT DISTINCT DATE(sre.evaluated_at) AS activityDate
            FROM study_review_events sre
            WHERE sre.user_id = :userId
                AND sre.source = 'EXAM_SUBMISSION'
            ORDER BY activityDate DESC
            """, nativeQuery = true)
    List<LocalDate> findActivityDatesByUser(@Param("userId") Long userId);

    // Timezone 'Asia/Ho_Chi_Minh' is hardcoded in SQL; keep in sync with AppConstants.APP_TIMEZONE
    @Query(value = """
            SELECT DATE(sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS activityDate
            FROM study_review_events sre
            WHERE sre.user_id = :userId
              AND sre.source = 'EXAM_SUBMISSION'
            GROUP BY DATE(sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
            HAVING COALESCE(SUM(sre.latency_ms), 0) >= :dailyTargetMs
            ORDER BY activityDate DESC
            """, nativeQuery = true)
    List<LocalDate> findQualifiedActivityDatesByUser(
            @Param("userId") Long userId,
            @Param("dailyTargetMs") long dailyTargetMs);

    @Query(value = """
            SELECT COUNT(*)
            FROM study_cards sc
            WHERE sc.user_id = :userId
                AND sc.next_review_at <= CURRENT_TIMESTAMP
            """, nativeQuery = true)
    Integer countDueCards(@Param("userId") Long userId);

    // Question-level stats for content analytics (shared with exam_service via
    // exam_tag link)
    @Query(value = """
            SELECT
                sre.item_id                         AS questionId,
                COUNT(*)                            AS totalAttempts,
                CASE WHEN COUNT(*) = 0 THEN 0
                     ELSE ROUND(SUM(CASE WHEN sre.is_correct = true THEN 1 ELSE 0 END)::numeric / COUNT(*) * 100, 2)
                END                                 AS correctRate,
                ROUND(AVG(sre.latency_ms), 0)      AS avgResponseTimeMs
            FROM study_review_events sre
            WHERE sre.item_id = :questionId
            GROUP BY sre.item_id
            """, nativeQuery = true)
    QuestionStatProjection findQuestionStats(@Param("questionId") Long questionId);

    interface TagStatProjection {
        Long getTagId();

        String getTagName();

        Integer getTotalQuestions();

        Integer getCorrectCount();

        Double getCorrectRate();
    }

    interface ScoreHistoryProjection {
        String getPeriod();

        Double getAvgScorePercent();

        Integer getAttemptCount();

        Double getAvgScoreRaw();
    }

    interface QuestionStatProjection {
        Long getQuestionId();

        Integer getTotalAttempts();

        Double getCorrectRate();

        Double getAvgResponseTimeMs();
    }

    interface OverallStatProjection {
        Long getTotalQuestions();

        Long getCorrectCount();

        Double getCorrectRate();
    }
}
