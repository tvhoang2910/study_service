package com.exam_bank.study_service.feature.analytics.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exam_bank.study_service.feature.analytics.dto.QuestionAnalyticsDto;
import com.exam_bank.study_service.feature.analytics.dto.RadarPointDto;
import com.exam_bank.study_service.feature.analytics.dto.ScoreHistoryDto;
import com.exam_bank.study_service.feature.analytics.dto.ScorePointDto;
import com.exam_bank.study_service.feature.analytics.dto.StudyStatsDto;
import com.exam_bank.study_service.feature.analytics.dto.WeaknessRadarDto;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.OverallStatProjection;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.QuestionStatProjection;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.ScoreHistoryProjection;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.TagStatProjection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long DAILY_STREAK_TARGET_MS = 15L * 60L * 1000L;

    private final StudyAnalyticsRepository analyticsRepository;

    public WeaknessRadarDto getWeaknessRadar(Long userId) {
        List<TagStatProjection> rows = analyticsRepository.findWeaknessByUser(userId);
        List<RadarPointDto> points = rows.stream()
                .map(r -> RadarPointDto.builder()
                        .tagId(r.getTagId())
                        .tagName(r.getTagName())
                        .totalQuestions(r.getTotalQuestions())
                        .correctCount(r.getCorrectCount())
                        .correctRate(r.getCorrectRate())
                        .build())
                .toList();

        if (points.isEmpty()) {
            OverallStatProjection overall = analyticsRepository.findOverallPerformanceByUser(userId);
            long totalQuestions = overall != null && overall.getTotalQuestions() != null
                    ? overall.getTotalQuestions()
                    : 0L;

            if (totalQuestions > 0) {
                long correctCount = overall.getCorrectCount() == null ? 0L : overall.getCorrectCount();
                double correctRate = overall.getCorrectRate() == null ? 0.0 : overall.getCorrectRate();

                points = List.of(RadarPointDto.builder()
                        .tagId(0L)
                        .tagName("Chua gan tag")
                        .totalQuestions((int) totalQuestions)
                        .correctCount((int) correctCount)
                        .correctRate(correctRate)
                        .build());

                log.info("Weakness radar fallback used for user {} because no tag analytics rows were found",
                        userId);
            }
        }

        return WeaknessRadarDto.builder().points(points).build();
    }

    public ScoreHistoryDto getScoreHistory(Long userId) {
        List<ScoreHistoryProjection> rows = analyticsRepository.findScoreHistory(userId);
        List<ScorePointDto> points = rows.stream()
                .map(r -> ScorePointDto.builder()
                        .period(r.getPeriod())
                        .avgScorePercent(r.getAvgScorePercent())
                        .attemptCount(r.getAttemptCount())
                        .avgScoreRaw(r.getAvgScoreRaw())
                        .build())
                .toList();
        return ScoreHistoryDto.builder().points(points).build();
    }

    public StudyStatsDto getStudyStats(Long userId) {
        int totalAttempts = analyticsRepository.countTotalAttempts(userId);
        Double avgScore = analyticsRepository.avgScorePercent(userId);
        Long totalStudyMinutes = analyticsRepository.sumTotalStudyMinutes(userId);
        int streakDays = computeStreakDays(userId);
        Integer dueCardsCount = analyticsRepository.countDueCards(userId);

        return StudyStatsDto.builder()
                .totalAttempts(totalAttempts)
                .avgScorePercent(avgScore)
                .streakDays(streakDays)
                .totalStudyMinutes(totalStudyMinutes == null ? 0L : totalStudyMinutes)
                .dueCardsCount(dueCardsCount == null ? 0 : dueCardsCount)
                .build();
    }

    private int computeStreakDays(Long userId) {
        List<LocalDate> dates = analyticsRepository.findQualifiedActivityDatesByUser(userId, DAILY_STREAK_TARGET_MS);
        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate latest = dates.get(0);
        if (latest.isBefore(today.minusDays(1))) {
            return 0;
        }

        int streak = 1;
        LocalDate expectedPrevious = latest.minusDays(1);
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).isEqual(expectedPrevious)) {
                streak++;
                expectedPrevious = expectedPrevious.minusDays(1);
                continue;
            }
            break;
        }
        return streak;
    }

    public QuestionAnalyticsDto getQuestionStats(Long questionId) {
        QuestionStatProjection p = analyticsRepository.findQuestionStats(questionId);
        if (p == null) {
            return QuestionAnalyticsDto.builder()
                    .questionId(questionId)
                    .totalAttempts(0)
                    .correctRate(0.0)
                    .avgResponseTimeMs(0.0)
                    .difficultyIndex(1.0)
                    .build();
        }
        double correctRate = p.getCorrectRate() != null ? p.getCorrectRate() : 0.0;
        return QuestionAnalyticsDto.builder()
                .questionId(p.getQuestionId())
                .totalAttempts(p.getTotalAttempts())
                .correctRate(correctRate)
                .avgResponseTimeMs(p.getAvgResponseTimeMs())
                .difficultyIndex(Math.round((1.0 - correctRate / 100.0) * 100.0) / 100.0)
                .build();
    }
}
