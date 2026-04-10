package com.exam_bank.study_service.feature.analytics.service;

import com.exam_bank.study_service.feature.analytics.dto.QuestionAnalyticsDto;
import com.exam_bank.study_service.feature.analytics.dto.StudyStatsDto;
import com.exam_bank.study_service.feature.analytics.dto.WeaknessRadarDto;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.OverallStatProjection;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.QuestionStatProjection;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository.TagStatProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Unit Tests")
class AnalyticsServiceTest {

    @Mock
    private StudyAnalyticsRepository analyticsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("getWeaknessRadar maps repository rows into radar points")
    void getWeaknessRadarMapsTagRows() {
        TagStatProjection algebraProjection = tagProjection(3L, "Algebra", 12, 4, 33.33);
        when(analyticsRepository.findWeaknessByUser(7L))
            .thenReturn(List.of(algebraProjection));

        WeaknessRadarDto result = analyticsService.getWeaknessRadar(7L);

        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getPoints().getFirst().getTagId()).isEqualTo(3L);
        assertThat(result.getPoints().getFirst().getTagName()).isEqualTo("Algebra");
        assertThat(result.getPoints().getFirst().getTotalQuestions()).isEqualTo(12);
        assertThat(result.getPoints().getFirst().getCorrectCount()).isEqualTo(4);
        assertThat(result.getPoints().getFirst().getCorrectRate()).isEqualTo(33.33);
    }

    @Test
    @DisplayName("getWeaknessRadar uses overall fallback when no tag rows exist")
    void getWeaknessRadarUsesOverallFallback() {
        OverallStatProjection overall = overallProjection(20L, 9L, 45.0);
        when(analyticsRepository.findWeaknessByUser(8L)).thenReturn(List.of());
        when(analyticsRepository.findOverallPerformanceByUser(8L))
            .thenReturn(overall);

        WeaknessRadarDto result = analyticsService.getWeaknessRadar(8L);

        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getPoints().getFirst().getTagId()).isEqualTo(0L);
        assertThat(result.getPoints().getFirst().getTagName()).isEqualTo("Chua gan tag");
        assertThat(result.getPoints().getFirst().getTotalQuestions()).isEqualTo(20);
        assertThat(result.getPoints().getFirst().getCorrectCount()).isEqualTo(9);
        assertThat(result.getPoints().getFirst().getCorrectRate()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("getWeaknessRadar returns empty points when no stats are available")
    void getWeaknessRadarReturnsEmptyWhenNoStats() {
        OverallStatProjection overall = mock(OverallStatProjection.class);
        when(overall.getTotalQuestions()).thenReturn(0L);
        when(analyticsRepository.findWeaknessByUser(9L)).thenReturn(List.of());
        when(analyticsRepository.findOverallPerformanceByUser(9L))
            .thenReturn(overall);

        WeaknessRadarDto result = analyticsService.getWeaknessRadar(9L);

        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("getStudyStats defaults null counters and computes no streak")
    void getStudyStatsDefaultsNullCountersAndNoStreak() {
        when(analyticsRepository.countTotalAttempts(5L)).thenReturn(10);
        when(analyticsRepository.avgScorePercent(5L)).thenReturn(null);
        when(analyticsRepository.sumTotalStudyMinutes(5L)).thenReturn(null);
        when(analyticsRepository.findActivityDatesByUser(5L)).thenReturn(List.of());
        when(analyticsRepository.countDueCards(5L)).thenReturn(null);

        StudyStatsDto result = analyticsService.getStudyStats(5L);

        assertThat(result.getTotalAttempts()).isEqualTo(10);
        assertThat(result.getAvgScorePercent()).isNull();
        assertThat(result.getTotalStudyMinutes()).isEqualTo(0L);
        assertThat(result.getStreakDays()).isEqualTo(0);
        assertThat(result.getDueCardsCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getStudyStats computes streak from consecutive activity days")
    void getStudyStatsComputesStreakFromConsecutiveDays() {
        LocalDate today = LocalDate.now();
        when(analyticsRepository.countTotalAttempts(6L)).thenReturn(3);
        when(analyticsRepository.avgScorePercent(6L)).thenReturn(77.7);
        when(analyticsRepository.sumTotalStudyMinutes(6L)).thenReturn(120L);
        when(analyticsRepository.countDueCards(6L)).thenReturn(4);
        when(analyticsRepository.findActivityDatesByUser(6L))
                .thenReturn(List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)));

        StudyStatsDto result = analyticsService.getStudyStats(6L);

        assertThat(result.getStreakDays()).isEqualTo(3);
        assertThat(result.getDueCardsCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("getQuestionStats returns defaults when question has no analytics")
    void getQuestionStatsReturnsDefaultsWhenMissing() {
        when(analyticsRepository.findQuestionStats(100L)).thenReturn(null);

        QuestionAnalyticsDto result = analyticsService.getQuestionStats(100L);

        assertThat(result.getQuestionId()).isEqualTo(100L);
        assertThat(result.getTotalAttempts()).isEqualTo(0);
        assertThat(result.getCorrectRate()).isEqualTo(0.0);
        assertThat(result.getAvgResponseTimeMs()).isEqualTo(0.0);
        assertThat(result.getDifficultyIndex()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("getQuestionStats rounds difficultyIndex to two decimals")
    void getQuestionStatsRoundsDifficultyIndex() {
        QuestionStatProjection projection = questionProjection(200L, 25, 87.34, 1800.0);
        when(analyticsRepository.findQuestionStats(200L))
            .thenReturn(projection);

        QuestionAnalyticsDto result = analyticsService.getQuestionStats(200L);

        assertThat(result.getQuestionId()).isEqualTo(200L);
        assertThat(result.getTotalAttempts()).isEqualTo(25);
        assertThat(result.getCorrectRate()).isEqualTo(87.34);
        assertThat(result.getAvgResponseTimeMs()).isEqualTo(1800.0);
        assertThat(result.getDifficultyIndex()).isEqualTo(0.13);
    }

    private TagStatProjection tagProjection(Long tagId, String tagName, Integer total, Integer correct, Double rate) {
        TagStatProjection projection = mock(TagStatProjection.class);
        when(projection.getTagId()).thenReturn(tagId);
        when(projection.getTagName()).thenReturn(tagName);
        when(projection.getTotalQuestions()).thenReturn(total);
        when(projection.getCorrectCount()).thenReturn(correct);
        when(projection.getCorrectRate()).thenReturn(rate);
        return projection;
    }

    private OverallStatProjection overallProjection(Long total, Long correct, Double rate) {
        OverallStatProjection projection = mock(OverallStatProjection.class);
        when(projection.getTotalQuestions()).thenReturn(total);
        when(projection.getCorrectCount()).thenReturn(correct);
        when(projection.getCorrectRate()).thenReturn(rate);
        return projection;
    }

    private QuestionStatProjection questionProjection(Long id, Integer attempts, Double correctRate, Double avgMs) {
        QuestionStatProjection projection = mock(QuestionStatProjection.class);
        when(projection.getQuestionId()).thenReturn(id);
        when(projection.getTotalAttempts()).thenReturn(attempts);
        when(projection.getCorrectRate()).thenReturn(correctRate);
        when(projection.getAvgResponseTimeMs()).thenReturn(avgMs);
        return projection;
    }
}
