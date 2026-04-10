package com.exam_bank.study_service.feature.gamification.entity;

import java.time.Instant;

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
@Table(name = "user_achievements", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_achievement", columnNames = {
                "user_id", "achievement_code"
        })
}, indexes = {
        @Index(name = "idx_user_achievement_user", columnList = "user_id"),
        @Index(name = "idx_user_achievement_unlocked", columnList = "user_id,unlocked_at")
})
public class UserAchievement extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "achievement_code", nullable = false, length = 80)
        private String achievementCode;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;
}
