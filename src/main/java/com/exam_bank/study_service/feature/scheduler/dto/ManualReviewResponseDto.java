package com.exam_bank.study_service.feature.scheduler.dto;

import java.time.Instant;

public record ManualReviewResponseDto(
        Long cardId,
        Long itemId,
        Integer quality,
        Integer repetition,
        Integer intervalDays,
        Double easinessFactor,
        Instant nextReviewAt) {
}
