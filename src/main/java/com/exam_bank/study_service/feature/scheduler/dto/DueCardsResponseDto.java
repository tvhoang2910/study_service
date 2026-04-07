package com.exam_bank.study_service.feature.scheduler.dto;

import java.time.Instant;
import java.util.List;

public record DueCardsResponseDto(
        Instant generatedAt,
        Long dueCount,
        Integer limit,
        List<DueStudyCardDto> cards) {
}
