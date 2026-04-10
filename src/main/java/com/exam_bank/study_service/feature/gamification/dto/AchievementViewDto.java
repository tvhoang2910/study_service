package com.exam_bank.study_service.feature.gamification.dto;

import java.time.Instant;

import com.exam_bank.study_service.feature.gamification.entity.AchievementCode;

import lombok.Builder;

@Builder
public record AchievementViewDto(
        AchievementCode code,
        String name,
        String description,
        String icon,
        String groupName,
        Integer points,
        Boolean unlocked,
        Instant unlockedAt) {
}
