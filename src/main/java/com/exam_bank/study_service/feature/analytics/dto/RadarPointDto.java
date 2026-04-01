package com.exam_bank.study_service.feature.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarPointDto {
    private Long tagId;
    private String tagName;
    private Double correctRate;  // 0.0 – 100.0
    private Integer totalQuestions;
    private Integer correctCount;
}
