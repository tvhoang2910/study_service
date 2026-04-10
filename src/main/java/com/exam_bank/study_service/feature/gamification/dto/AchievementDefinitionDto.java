package com.exam_bank.study_service.feature.gamification.dto;

import lombok.Builder;

@Builder
public record AchievementDefinitionDto(
        String code,
        String name,
        String description,
        String icon,
        String groupName,
        Integer points,
        Boolean active,
        String autoUnlockRule,
        String ruleType,
        Integer ruleThreshold,
        Integer ruleThresholdSecondary,
        String ruleConfigJson) {
}
