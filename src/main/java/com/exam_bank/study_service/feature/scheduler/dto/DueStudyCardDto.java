package com.exam_bank.study_service.feature.scheduler.dto;

import java.time.Instant;

public record DueStudyCardDto(
        Long cardId,
        Long itemId,
        Instant nextReviewAt,
        Integer repetition,
        Integer intervalDays,
        Double easinessFactor,
        Integer lastQuality,
        Boolean lastIsCorrect,
        Integer totalReviews,
        Integer correctReviews,
        String topicTagIds) {
}
