package com.exam_bank.study_service.feature.gamification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAchievementUpdateRequestDto(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Size(max = 80) String icon,
        @NotBlank @Size(max = 120) String groupName,
        @NotNull @Min(0) @Max(100000) Integer points,
        @NotNull Boolean active,
        @Size(max = 80) String autoUnlockRule,
        @Size(max = 80) String ruleType,
        @Min(0) @Max(100000) Integer ruleThreshold,
        @Min(0) @Max(100000) Integer ruleThresholdSecondary,
        @Size(max = 4000) String ruleConfigJson) {
}
