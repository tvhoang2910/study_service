package com.exam_bank.study_service.feature.scheduler.dto;

import java.time.Instant;
import java.util.List;

public record Sm2ExamDecksResponseDto(
        Instant generatedAt,
        Integer deckCount,
        Integer totalWrongQuestions,
        List<Sm2ExamDeckDto> decks) {
}
