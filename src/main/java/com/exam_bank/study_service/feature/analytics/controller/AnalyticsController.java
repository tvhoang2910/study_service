package com.exam_bank.study_service.feature.analytics.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exam_bank.study_service.feature.analytics.dto.QuestionAnalyticsDto;
import com.exam_bank.study_service.feature.analytics.dto.ScoreHistoryDto;
import com.exam_bank.study_service.feature.analytics.dto.StudyStatsDto;
import com.exam_bank.study_service.feature.analytics.dto.WeaknessRadarDto;
import com.exam_bank.study_service.feature.analytics.service.AnalyticsService;
import com.exam_bank.study_service.service.AuthenticatedUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthenticatedUserService userService;

    @GetMapping("/me/weakness-radar")
    public ResponseEntity<WeaknessRadarDto> getWeaknessRadar() {
        Long userId = userService.getCurrentUserId();
        WeaknessRadarDto result = analyticsService.getWeaknessRadar(userId);
        log.info("getWeaknessRadar: userId={}, points={}", userId, result.getPoints().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me/score-history")
    public ResponseEntity<ScoreHistoryDto> getScoreHistory() {
        Long userId = userService.getCurrentUserId();
        ScoreHistoryDto result = analyticsService.getScoreHistory(userId);
        log.info("getScoreHistory: userId={}, periods={}", userId, result.getPoints().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me/stats")
    public ResponseEntity<StudyStatsDto> getStudyStats() {
        Long userId = userService.getCurrentUserId();
        log.debug("getStudyStats: userId={}", userId);
        return ResponseEntity.ok(analyticsService.getStudyStats(userId));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionAnalyticsDto> getQuestionStats(@PathVariable Long questionId) {
        log.debug("getQuestionStats: questionId={}", questionId);
        return ResponseEntity.ok(analyticsService.getQuestionStats(questionId));
    }
}
