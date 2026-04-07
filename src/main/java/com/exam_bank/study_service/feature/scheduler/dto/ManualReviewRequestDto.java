package com.exam_bank.study_service.feature.scheduler.dto;

import jakarta.validation.constraints.NotNull;

public record ManualReviewRequestDto(
        @NotNull Long itemId,
        @NotNull Boolean isCorrect,
        Long responseTimeMs,
        Integer answerChangeCount) {
}
