package com.exam_bank.study_service.feature.review.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import com.exam_bank.study_service.shared.entity.BaseEntity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "study_review_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sre_user_item_attempt_source", columnNames = {
                "user_id", "item_id", "attempt_id", "source"
        })
}, indexes = {
        @Index(name = "idx_sre_user_item_eval", columnList = "user_id,item_id,evaluated_at"),
        @Index(name = "idx_sre_attempt", columnList = "attempt_id"),
        @Index(name = "idx_sre_user_eval", columnList = "user_id,evaluated_at")
}, check = {
        @CheckConstraint(name = "chk_sre_quality", constraint = "quality between 0 and 5")
})
public class StudyReviewEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "quality", nullable = false)
    private Integer quality;

    @Column(name = "is_correct", nullable = false)
    @ColumnDefault("false")
    private Boolean isCorrect = false;

    @Column(name = "score_earned", nullable = false)
    @ColumnDefault("0")
    private Double scoreEarned = 0.0;

    @Column(name = "score_max", nullable = false)
    @ColumnDefault("0")
    private Double scoreMax = 0.0;

    @Column(name = "score_percent", nullable = false)
    @ColumnDefault("0")
    private Double scorePercent = 0.0;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "answer_change_count", nullable = false)
    @ColumnDefault("0")
    private Integer answerChangeCount = 0;

    @Column(name = "selected_option_ids", length = 500)
    private String selectedOptionIds;

    @Column(name = "correct_option_ids", length = 500)
    private String correctOptionIds;

    @Column(name = "topic_tag_ids", length = 500)
    private String topicTagIds;

    @Column(name = "difficulty")
    private Double difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private ReviewSource source = ReviewSource.EXAM_SUBMISSION;
}