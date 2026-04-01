package com.exam_bank.study_service.feature.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyStatsDto {
    private Integer totalAttempts;
    private Double avgScorePercent;
    private Integer streakDays;
    private Long totalStudyMinutes;
    private Integer dueCardsCount;
}
