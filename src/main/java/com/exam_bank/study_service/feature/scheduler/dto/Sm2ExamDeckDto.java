package com.exam_bank.study_service.feature.scheduler.dto;

import java.time.Instant;
import java.util.List;

public record Sm2ExamDeckDto(
        Long examId,
        String examTitle,
        Long latestAttemptId,
        Instant latestSubmittedAt,
        Integer wrongQuestionCount,
        List<Sm2DeckQuestionDto> questions) {
}
