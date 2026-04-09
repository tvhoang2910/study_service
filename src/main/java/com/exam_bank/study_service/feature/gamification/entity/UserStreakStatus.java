package com.exam_bank.study_service.feature.gamification.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import com.exam_bank.study_service.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_streak_status", uniqueConstraints = {
        @UniqueConstraint(name = "uq_streak_user", columnNames = "user_id")
}, indexes = {
        @Index(name = "idx_streak_user", columnList = "user_id")
})
public class UserStreakStatus extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "current_streak", nullable = false)
    @ColumnDefault("0")
    private Integer currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    @ColumnDefault("0")
    private Integer longestStreak = 0;

    @Column(name = "last_qualified_date")
    private LocalDate lastQualifiedDate;

    @Column(name = "streak_updated_at")
    private Instant streakUpdatedAt;
}
