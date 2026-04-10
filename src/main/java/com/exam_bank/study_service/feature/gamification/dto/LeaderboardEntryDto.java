package com.exam_bank.study_service.feature.gamification.dto;

import lombok.Builder;

@Builder
public record LeaderboardEntryDto(
        Integer rank,
        Long userId,
        String displayName,
        Integer points,
        Integer streakDays,
        Integer unlockedAchievements,
        Boolean currentUser) {
}
