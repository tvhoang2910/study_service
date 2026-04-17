package com.exam_bank.study_service.feature.review.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;

@Repository
public interface StudyReviewEventRepository extends JpaRepository<StudyReviewEvent, Long> {

  @Query("select e from StudyReviewEvent e where e.attemptId = :attemptId and e.itemId = :itemId")
  Optional<StudyReviewEvent> findByAttemptIdAndItemId(
      @Param("attemptId") Long attemptId,
      @Param("itemId") Long itemId);

  /**
   * Returns wrong questions across ALL exam attempts (not just the latest),
   * ordered by submission time descending. Each row includes the attempt number
   * so callers can group or filter by attempt as needed.
   *
   * <p>
   * Breaking change: previously returned only the latest attempt per exam.
   */
  @Query(value = """
      with exam_attempts as (
          select e.user_id,
                 e.exam_id,
                 e.attempt_id,
                 max(e.evaluated_at) as submitted_at,
                 row_number() over (
                     partition by e.user_id, e.exam_id
                     order by max(e.evaluated_at) asc, e.attempt_id asc
                 ) as attempt_number
          from study_review_events e
          where e.user_id = :userId
            and e.source = 'EXAM_SUBMISSION'
          group by e.user_id, e.exam_id, e.attempt_id
      )
      select e.exam_id as examId,
             max(coalesce(e.exam_title, concat('Exam #', e.exam_id))) as examTitle,
             e.attempt_id as attemptId,
             ea.submitted_at as submittedAt,
             ea.attempt_number as attemptNumber,
             e.item_id as itemId,
             max(e.topic_tag_ids) as topicTagIds,
             max(e.selected_option_ids) as selectedOptionIds,
             max(e.correct_option_ids) as correctOptionIds
      from study_review_events e
      join exam_attempts ea
        on ea.user_id = e.user_id
       and ea.exam_id = e.exam_id
       and ea.attempt_id = e.attempt_id
      where e.user_id = :userId
        and e.source = 'EXAM_SUBMISSION'
        and e.is_correct = false
      group by e.exam_id, e.attempt_id, ea.submitted_at, ea.attempt_number, e.item_id
      order by ea.submitted_at desc, e.exam_id asc, e.attempt_id desc, e.item_id asc
      """, nativeQuery = true)
  List<LatestWrongQuestionProjection> findLatestWrongQuestionsByExam(@Param("userId") Long userId);

  @Query(value = """
      SELECT sre.user_id
      FROM study_review_events sre
      GROUP BY sre.user_id
      ORDER BY MAX(sre.evaluated_at) DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<Long> findRecentActiveUserIds(@Param("limit") int limit);

  @Query("""
      SELECT COUNT(sre)
      FROM StudyReviewEvent sre
      WHERE sre.userId = :userId
        AND sre.isCorrect = true
        AND sre.evaluatedAt >= :fromInstant
        AND sre.evaluatedAt < :toInstant
      """)
  long countCorrectAnswersByUserBetween(
      @Param("userId") Long userId,
      @Param("fromInstant") Instant fromInstant,
      @Param("toInstant") Instant toInstant);

  @Query(value = """
      SELECT COALESCE(SUM(sre.latency_ms), 0)
      FROM study_review_events sre
      WHERE sre.user_id = :userId
        AND sre.evaluated_at >= :fromInstant
        AND sre.evaluated_at < :toInstant
      """, nativeQuery = true)
  long sumStudyDurationMsByUserBetween(
      @Param("userId") Long userId,
      @Param("fromInstant") Instant fromInstant,
      @Param("toInstant") Instant toInstant);

  @Query("""
      SELECT COUNT(DISTINCT sre.attemptId)
      FROM StudyReviewEvent sre
      WHERE sre.userId = :userId
        AND sre.source = com.exam_bank.study_service.feature.review.entity.ReviewSource.EXAM_SUBMISSION
        AND sre.evaluatedAt >= :fromInstant
        AND sre.evaluatedAt < :toInstant
      """)
  long countDistinctExamAttemptsByUserBetween(
      @Param("userId") Long userId,
      @Param("fromInstant") Instant fromInstant,
      @Param("toInstant") Instant toInstant);

  @Query(value = """
      SELECT COALESCE(SUM(sre.answer_change_count), 0)
      FROM study_review_events sre
      WHERE sre.user_id = :userId
      """, nativeQuery = true)
  long sumAnswerChangesByUser(@Param("userId") Long userId);

  // Timezone 'Asia/Ho_Chi_Minh' is hardcoded in SQL; keep in sync with
  // AppConstants.APP_TIMEZONE
  @Query(value = """
      SELECT COUNT(*)
      FROM study_review_events sre
      WHERE sre.user_id = :userId
        AND EXTRACT(HOUR FROM (sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')) BETWEEN 0 AND 4
      """, nativeQuery = true)
  long countNightOwlReviewsByUser(@Param("userId") Long userId);

  @Query(value = """
      SELECT COUNT(*)
      FROM (
        SELECT sre.attempt_id
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
        GROUP BY sre.attempt_id
      ) attempts
      """, nativeQuery = true)
  long countDistinctExamAttemptsByUser(@Param("userId") Long userId);

  @Query(value = """
      WITH attempt_scores AS (
        SELECT sre.attempt_id,
               (COALESCE(SUM(sre.score_earned), 0) / NULLIF(COALESCE(SUM(sre.score_max), 0), 0)) * 100.0 AS score_percent
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
        GROUP BY sre.attempt_id
      )
      SELECT COUNT(*)
      FROM attempt_scores s
      WHERE s.score_percent >= :targetScorePercent
      """, nativeQuery = true)
  long countAttemptByUserWithMinScore(
      @Param("userId") Long userId,
      @Param("targetScorePercent") double targetScorePercent);

  @Query(value = """
      WITH attempt_metrics AS (
        SELECT sre.attempt_id,
               (COALESCE(SUM(sre.score_earned), 0) / NULLIF(COALESCE(SUM(sre.score_max), 0), 0)) * 100.0 AS score_percent,
               COALESCE(SUM(sre.latency_ms), 0) AS duration_ms,
               COUNT(*) AS question_count
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
        GROUP BY sre.attempt_id
      )
      SELECT COUNT(*)
      FROM attempt_metrics m
      WHERE m.score_percent >= :targetScorePercent
        AND m.duration_ms > 0
        AND m.duration_ms <= (m.question_count * :maxLatencyPerQuestionMs)
      """, nativeQuery = true)
  long countFastHighScoreAttempts(
      @Param("userId") Long userId,
      @Param("targetScorePercent") double targetScorePercent,
      @Param("maxLatencyPerQuestionMs") long maxLatencyPerQuestionMs);

  @Query(value = """
      WITH ordered AS (
        SELECT sre.attempt_id,
               sre.is_correct,
               ROW_NUMBER() OVER (PARTITION BY sre.attempt_id ORDER BY sre.id) AS rn_all,
               ROW_NUMBER() OVER (PARTITION BY sre.attempt_id, sre.is_correct ORDER BY sre.id) AS rn_state
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
      ), correct_groups AS (
        SELECT o.attempt_id,
               (o.rn_all - o.rn_state) AS grp,
               COUNT(*) AS streak_len
        FROM ordered o
        WHERE o.is_correct = true
        GROUP BY o.attempt_id, (o.rn_all - o.rn_state)
      )
      SELECT COUNT(*)
      FROM correct_groups g
      WHERE g.streak_len >= :minStreak
      """, nativeQuery = true)
  long countAttemptsHavingCorrectAnswerStreak(
      @Param("userId") Long userId,
      @Param("minStreak") int minStreak);

  @Query(value = """
      WITH attempt_scores AS (
        SELECT sre.attempt_id,
               (COALESCE(SUM(sre.score_earned), 0) / NULLIF(COALESCE(SUM(sre.score_max), 0), 0)) * 100.0 AS score_percent,
               MAX(NULLIF(split_part(COALESCE(sre.topic_tag_ids, ''), ',', 1), '')) AS subject_tag
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
        GROUP BY sre.attempt_id
      ), by_subject AS (
        SELECT s.subject_tag,
               COUNT(*) AS total_attempts,
               SUM(CASE WHEN s.score_percent >= :targetScorePercent THEN 1 ELSE 0 END) AS good_attempts
        FROM attempt_scores s
        WHERE s.subject_tag IS NOT NULL
        GROUP BY s.subject_tag
      )
      SELECT COUNT(*)
      FROM by_subject b
      WHERE b.total_attempts >= :minAttemptsPerSubject
        AND b.total_attempts = b.good_attempts
      """, nativeQuery = true)
  long countSubjectsWhereAllAttemptsAreGood(
      @Param("userId") Long userId,
      @Param("targetScorePercent") double targetScorePercent,
      @Param("minAttemptsPerSubject") int minAttemptsPerSubject);

  // Timezone 'Asia/Ho_Chi_Minh' is hardcoded in SQL; keep in sync with
  // AppConstants.APP_TIMEZONE
  @Query(value = """
      SELECT COUNT(*)
      FROM (
        SELECT sre.attempt_id
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
          AND EXTRACT(ISODOW FROM (sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')) IN (6, 7)
        GROUP BY sre.attempt_id
      ) weekend_attempts
      """, nativeQuery = true)
  long countWeekendAttemptsByUser(@Param("userId") Long userId);

  @Query(value = """
      SELECT COUNT(DISTINCT sre.exam_id)
      FROM study_review_events sre
      WHERE sre.user_id = :userId
        AND sre.source = 'EXAM_SUBMISSION'
      """, nativeQuery = true)
  long countDistinctExamByUser(@Param("userId") Long userId);

  @Query(value = """
      WITH attempt_scores AS (
        SELECT sre.exam_id,
               sre.attempt_id,
               MAX(sre.evaluated_at) AS submitted_at,
               (COALESCE(SUM(sre.score_earned), 0) / NULLIF(COALESCE(SUM(sre.score_max), 0), 0)) * 100.0 AS score_percent
        FROM study_review_events sre
        WHERE sre.user_id = :userId
          AND sre.source = 'EXAM_SUBMISSION'
        GROUP BY sre.exam_id, sre.attempt_id
      )
      SELECT COUNT(*)
      FROM attempt_scores low
      JOIN attempt_scores high
        ON high.exam_id = low.exam_id
       AND high.submitted_at > low.submitted_at
      WHERE low.score_percent < :failScorePercent
        AND high.score_percent >= :goodScorePercent
      """, nativeQuery = true)
  long countRetakeImprovementExams(
      @Param("userId") Long userId,
      @Param("failScorePercent") double failScorePercent,
      @Param("goodScorePercent") double goodScorePercent);

  @Query(value = """
      WITH global_subjects AS (
        SELECT DISTINCT NULLIF(split_part(COALESCE(sre.topic_tag_ids, ''), ',', 1), '') AS subject_tag
        FROM study_review_events sre
        WHERE sre.source = 'EXAM_SUBMISSION'
      ), user_subjects AS (
        SELECT DISTINCT NULLIF(split_part(COALESCE(sre.topic_tag_ids, ''), ',', 1), '') AS subject_tag
        FROM study_review_events sre
        WHERE sre.source = 'EXAM_SUBMISSION'
          AND sre.user_id = :userId
      )
      SELECT
        (SELECT COUNT(*) FROM user_subjects us WHERE us.subject_tag IS NOT NULL) AS user_count,
        (SELECT COUNT(*) FROM global_subjects gs WHERE gs.subject_tag IS NOT NULL) AS global_count
      """, nativeQuery = true)
  SubjectCoverageProjection getSubjectCoverage(@Param("userId") Long userId);

  // Timezone 'Asia/Ho_Chi_Minh' is hardcoded in SQL; keep in sync with
  // AppConstants.APP_TIMEZONE
  @Query(value = """
      SELECT DISTINCT DATE(sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS activityDate
      FROM study_review_events sre
      WHERE sre.user_id = :userId
        AND sre.evaluated_at >= :fromInstant
        AND sre.evaluated_at < :toInstant
      ORDER BY activityDate ASC
      """, nativeQuery = true)
  List<java.time.LocalDate> findActivityDatesByUserBetween(
      @Param("userId") Long userId,
      @Param("fromInstant") Instant fromInstant,
      @Param("toInstant") Instant toInstant);

  // Timezone 'Asia/Ho_Chi_Minh' is hardcoded in SQL; keep in sync with
  // AppConstants.APP_TIMEZONE
  @Query(value = """
      SELECT DATE(sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS qualifiedDate
      FROM study_review_events sre
      WHERE sre.user_id = :userId
        AND sre.evaluated_at >= :fromInstant
        AND sre.evaluated_at < :toInstant
      GROUP BY DATE(sre.evaluated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
      HAVING COALESCE(SUM(sre.latency_ms), 0) >= :dailyTargetMs
      ORDER BY qualifiedDate ASC
      """, nativeQuery = true)
  List<java.time.LocalDate> findQualifiedDatesByUserBetween(
      @Param("userId") Long userId,
      @Param("fromInstant") Instant fromInstant,
      @Param("toInstant") Instant toInstant,
      @Param("dailyTargetMs") long dailyTargetMs);

  interface LatestWrongQuestionProjection {
    Long getExamId();

    String getExamTitle();

    Long getAttemptId();

    Instant getSubmittedAt();

    Integer getAttemptNumber();

    Long getItemId();

    String getTopicTagIds();

    String getSelectedOptionIds();

    String getCorrectOptionIds();
  }

  interface SubjectCoverageProjection {
    Long getUserCount();

    Long getGlobalCount();
  }
}
