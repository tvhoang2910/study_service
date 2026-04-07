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

    @Query(value = """
            with latest_attempts as (
                select user_id,
                       exam_id,
                       attempt_id,
                       max(evaluated_at) as submitted_at,
                       row_number() over (
                           partition by user_id, exam_id
                           order by max(evaluated_at) desc, attempt_id desc
                       ) as rn
                from study_review_events
                where user_id = :userId
                                    and source = 'EXAM_SUBMISSION'
                group by user_id, exam_id, attempt_id
            )
            select e.exam_id as examId,
                   max(coalesce(e.exam_title, concat('Exam #', e.exam_id))) as examTitle,
                   e.attempt_id as attemptId,
                   max(e.evaluated_at) as submittedAt,
                   e.item_id as itemId,
                     max(e.topic_tag_ids) as topicTagIds,
                     max(e.selected_option_ids) as selectedOptionIds,
                     max(e.correct_option_ids) as correctOptionIds
            from study_review_events e
            join latest_attempts la
              on la.user_id = e.user_id
             and la.exam_id = e.exam_id
             and la.attempt_id = e.attempt_id
            where e.user_id = :userId
              and la.rn = 1
                            and e.source = 'EXAM_SUBMISSION'
              and e.is_correct = false
            group by e.exam_id, e.attempt_id, e.item_id
            order by max(e.evaluated_at) desc, e.exam_id asc, e.item_id asc
            """, nativeQuery = true)
    List<LatestWrongQuestionProjection> findLatestWrongQuestionsByExam(@Param("userId") Long userId);

    interface LatestWrongQuestionProjection {
        Long getExamId();

        String getExamTitle();

        Long getAttemptId();

        Instant getSubmittedAt();

        Long getItemId();

        String getTopicTagIds();

        String getSelectedOptionIds();

        String getCorrectOptionIds();
    }
}
