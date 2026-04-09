package com.exam_bank.study_service.feature.gamification.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record CalendarDayDto(
        LocalDate date,
        Boolean activityCompleted,
        Boolean streakQualified) {
}
