package com.exam_bank.study_service.feature.gamification.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record StreakCalendarDto(
        String month,
        Integer totalDays,
        Integer activityDays,
        Integer qualifiedDays,
        List<CalendarDayDto> days) {
}
