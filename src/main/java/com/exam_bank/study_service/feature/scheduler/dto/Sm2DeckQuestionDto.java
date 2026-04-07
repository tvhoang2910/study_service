package com.exam_bank.study_service.feature.scheduler.dto;

import java.time.Instant;

public record Sm2DeckQuestionDto(
        Long itemId,
        String topicTagIds,
        String selectedOptionIds,
        String correctOptionIds,
        Integer repetition,
        Integer intervalDays,
        Double easinessFactor,
        Instant nextReviewAt,
        Boolean dueNow,
        Integer totalReviews,
        Integer correctReviews) {
}
