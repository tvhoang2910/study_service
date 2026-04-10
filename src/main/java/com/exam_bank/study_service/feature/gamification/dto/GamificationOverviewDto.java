package com.exam_bank.study_service.feature.gamification.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record GamificationOverviewDto(
        Integer streakDays,
        Integer longestStreak,
        Integer dailyStudyMinutes,
        Integer dailyTargetMinutes,
        Boolean todayQualified,
        Boolean justQualifiedToday,
        Integer points,
        List<AchievementViewDto> newlyUnlockedAchievements,
        List<AchievementViewDto> recentUnlockedAchievements) {
}
