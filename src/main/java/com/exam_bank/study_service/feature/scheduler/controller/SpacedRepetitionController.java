package com.exam_bank.study_service.feature.scheduler.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.exam_bank.study_service.feature.scheduler.dto.DueCardsResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.ManualReviewRequestDto;
import com.exam_bank.study_service.feature.scheduler.dto.ManualReviewResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDecksResponseDto;
import com.exam_bank.study_service.feature.scheduler.service.SpacedRepetitionService;
import com.exam_bank.study_service.service.AuthenticatedUserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/spaced-repetition")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SpacedRepetitionController {

    private final SpacedRepetitionService spacedRepetitionService;
    private final AuthenticatedUserService userService;

    @GetMapping("/me/due")
    public ResponseEntity<DueCardsResponseDto> getDueCards(
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        Long userId = userService.getCurrentUserId();
        DueCardsResponseDto response = spacedRepetitionService.getDueCards(userId, limit);
        log.debug("getDueCards: userId={}, limit={}, dueCount={}", userId, limit, response.dueCount());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/exam-decks")
    public ResponseEntity<Sm2ExamDecksResponseDto> getExamDecks() {
        Long userId = userService.getCurrentUserId();
        Sm2ExamDecksResponseDto response = spacedRepetitionService.getExamDecks(userId);
        log.debug("getExamDecks: userId={}, deckCount={}, totalWrongQuestions={}",
                userId,
                response.deckCount(),
                response.totalWrongQuestions());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/review")
    public ResponseEntity<ManualReviewResponseDto> submitManualReview(@Valid @RequestBody ManualReviewRequestDto request) {
        Long userId = userService.getCurrentUserId();
        ManualReviewResponseDto response = spacedRepetitionService.submitManualReview(
            userId,
            request.itemId(),
            request.isCorrect(),
            request.responseTimeMs(),
            request.answerChangeCount());
        log.info("submitManualReview: userId={}, itemId={}, isCorrect={}, computedQuality={}, nextReviewAt={}",
            userId,
            request.itemId(),
            request.isCorrect(),
            response.quality(),
            response.nextReviewAt());
        return ResponseEntity.ok(response);
    }
}
