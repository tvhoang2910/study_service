package com.exam_bank.study_service.feature.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorePointDto {
    private String period;           // "2026-03"
    private Double avgScorePercent;  // 0.0 – 100.0
    private Integer attemptCount;
    private Double avgScoreRaw;
}
