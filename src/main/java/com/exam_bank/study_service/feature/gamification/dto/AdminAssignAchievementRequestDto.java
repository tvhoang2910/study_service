package com.exam_bank.study_service.feature.gamification.dto;

import jakarta.validation.constraints.NotNull;

public record AdminAssignAchievementRequestDto(@NotNull Long userId) {
}
