package com.exam_bank.study_service.feature.scheduler.entity;

import java.time.Instant;

import com.exam_bank.study_service.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "study_card_review_history", indexes = {
        @Index(name = "idx_scrh_card_reviewed", columnList = "card_id,reviewed_at"),
        @Index(name = "idx_scrh_event", columnList = "review_event_id")
})
public class StudyCardReviewHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private StudyCard card;

    @Column(name = "review_event_id", nullable = false)
    private Long reviewEventId;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "quality", nullable = false)
    private Integer quality;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "prev_repetition", nullable = false)
    private Integer prevRepetition;

    @Column(name = "prev_interval_days", nullable = false)
    private Integer prevIntervalDays;

    @Column(name = "prev_easiness_factor", nullable = false)
    private Double prevEasinessFactor;

    @Column(name = "next_repetition", nullable = false)
    private Integer nextRepetition;

    @Column(name = "next_interval_days", nullable = false)
    private Integer nextIntervalDays;

    @Column(name = "next_easiness_factor", nullable = false)
    private Double nextEasinessFactor;

    @Column(name = "next_review_at", nullable = false)
    private Instant nextReviewAt;
}