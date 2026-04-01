package com.exam_bank.study_service.feature.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.exam_bank.study_service.feature.analytics.dto.StudyStatsDto;
import com.exam_bank.study_service.feature.analytics.repository.StudyAnalyticsRepository;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private StudyAnalyticsRepository analyticsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getStudyStats_shouldMapTotalStudyMinutesFromRepository() {
        Long userId = 6L;
        LocalDate today = LocalDate.now();
        when(analyticsRepository.countTotalAttempts(userId)).thenReturn(8);
        when(analyticsRepository.avgScorePercent(userId)).thenReturn(72.5);
        when(analyticsRepository.sumTotalStudyMinutes(userId)).thenReturn(41L);
        when(analyticsRepository.countDueCards(userId)).thenReturn(3);
        when(analyticsRepository.findActivityDatesByUser(userId)).thenReturn(List.of(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(4)));

        StudyStatsDto result = analyticsService.getStudyStats(userId);

        assertThat(result.getTotalAttempts()).isEqualTo(8);
        assertThat(result.getAvgScorePercent()).isEqualTo(72.5);
        assertThat(result.getTotalStudyMinutes()).isEqualTo(41L);
        assertThat(result.getStreakDays()).isEqualTo(3);
        assertThat(result.getDueCardsCount()).isEqualTo(3);
    }

    @Test
    void getStudyStats_shouldDefaultTotalStudyMinutesToZero_whenRepositoryReturnsNull() {
        Long userId = 7L;
        LocalDate today = LocalDate.now();
        when(analyticsRepository.countTotalAttempts(userId)).thenReturn(0);
        when(analyticsRepository.avgScorePercent(userId)).thenReturn(null);
        when(analyticsRepository.sumTotalStudyMinutes(userId)).thenReturn(null);
        when(analyticsRepository.countDueCards(userId)).thenReturn(null);
        when(analyticsRepository.findActivityDatesByUser(userId)).thenReturn(List.of(
                today.minusDays(3),
                today.minusDays(4)));

        StudyStatsDto result = analyticsService.getStudyStats(userId);

        assertThat(result.getTotalStudyMinutes()).isZero();
        assertThat(result.getStreakDays()).isZero();
        assertThat(result.getDueCardsCount()).isZero();
    }
}
