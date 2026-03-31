package com.exam_bank.study_service.feature.scheduler.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import com.exam_bank.study_service.shared.entity.BaseEntity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "study_cards", uniqueConstraints = {
        @UniqueConstraint(name = "uq_study_cards_user_item", columnNames = {
                "user_id", "item_id"
        })
}, indexes = {
        @Index(name = "idx_study_cards_due", columnList = "next_review_at"),
        @Index(name = "idx_study_cards_user_due", columnList = "user_id,next_review_at"),
        @Index(name = "idx_study_cards_user_item", columnList = "user_id,item_id")
}, check = {
        @CheckConstraint(name = "chk_study_card_ef", constraint = "easiness_factor >= 1.3"),
        @CheckConstraint(name = "chk_study_card_interval", constraint = "interval_days >= 0"),
        @CheckConstraint(name = "chk_study_card_repetition", constraint = "repetition >= 0")
})
public class StudyCard extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "last_attempt_id")
    private Long lastAttemptId;

    @Column(name = "topic_tag_ids", length = 500)
    private String topicTagIds;

    @Column(name = "repetition", nullable = false)
    @ColumnDefault("0")
    private Integer repetition = 0;

    @Column(name = "interval_days", nullable = false)
    @ColumnDefault("0")
    private Integer intervalDays = 0;

    @Column(name = "easiness_factor", nullable = false)
    @ColumnDefault("2.5")
    private Double easinessFactor = 2.5;

    @Column(name = "next_review_at", nullable = false)
    private Instant nextReviewAt = Instant.now();

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "last_quality")
    private Integer lastQuality;

    @Column(name = "last_is_correct")
    private Boolean lastIsCorrect;

    @Column(name = "total_reviews", nullable = false)
    @ColumnDefault("0")
    private Integer totalReviews = 0;

    @Column(name = "correct_reviews", nullable = false)
    @ColumnDefault("0")
    private Integer correctReviews = 0;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;
}