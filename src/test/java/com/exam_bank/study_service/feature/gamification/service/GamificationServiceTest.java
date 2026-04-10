package com.exam_bank.study_service.feature.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.entity.AchievementCode;
import com.exam_bank.study_service.feature.gamification.entity.UserAchievement;
import com.exam_bank.study_service.feature.gamification.entity.UserStreakStatus;
import com.exam_bank.study_service.feature.gamification.repository.UserAchievementRepository;
import com.exam_bank.study_service.feature.gamification.repository.UserStreakStatusRepository;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private StudyReviewEventRepository reviewEventRepository;

    @Mock
    private UserStreakStatusRepository streakStatusRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @InjectMocks
    private GamificationService service;

    @Test
    void getAchievements_shouldUnlockScholar_whenDailyStudyMinutesReachFive() {
        UserStreakStatus status = new UserStreakStatus();
        status.setUserId(9L);
        status.setCurrentStreak(0);
        status.setLongestStreak(0);

        when(streakStatusRepository.findByUserId(9L)).thenReturn(Optional.of(status));
        when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(9L), any(), any())).thenReturn(5L * 60_000L);
        when(userAchievementRepository.findByUserId(9L)).thenReturn(List.of());

        List<AchievementViewDto> achievements = service.getAchievements(9L);

        AchievementViewDto scholar = achievements.stream()
                .filter(item -> item.code() == AchievementCode.SCHOLAR)
                .findFirst()
                .orElseThrow();

        assertThat(scholar.unlocked()).isTrue();
        assertThat(scholar.description()).contains("5 phút");
    }

    @Test
    void getOverview_shouldIncludeRuntimeEligiblePoints_withoutPersistingIncompatibleCodes() {
        UserStreakStatus status = new UserStreakStatus();
        status.setUserId(15L);
        status.setCurrentStreak(0);
        status.setLongestStreak(0);

        when(streakStatusRepository.findByUserId(15L)).thenReturn(Optional.of(status));
        when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(15L), any(), any())).thenReturn(6L * 60_000L);
        when(userAchievementRepository.findByUserId(15L)).thenReturn(List.of());
        when(userAchievementRepository.findTop5ByUserIdOrderByUnlockedAtDesc(15L)).thenReturn(List.of());

        GamificationOverviewDto overview = service.getOverview(15L);

        assertThat(overview.dailyStudyMinutes()).isEqualTo(6);
        assertThat(overview.dailyTargetMinutes()).isEqualTo(15);
        assertThat(overview.todayQualified()).isFalse();
        assertThat(overview.points()).isGreaterThanOrEqualTo(300);

        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void getLeaderboard_shouldRankUsersByPoints_andIncludeCurrentUserFlag() {
        UserStreakStatus currentUserStatus = new UserStreakStatus();
        currentUserStatus.setUserId(1L);
        currentUserStatus.setCurrentStreak(1);

        UserStreakStatus otherUserStatus = new UserStreakStatus();
        otherUserStatus.setUserId(2L);
        otherUserStatus.setCurrentStreak(4);

        when(reviewEventRepository.findRecentActiveUserIds(15)).thenReturn(List.of(2L, 1L));
        when(streakStatusRepository.findByUserId(1L)).thenReturn(Optional.of(currentUserStatus));
        when(streakStatusRepository.findByUserId(2L)).thenReturn(Optional.of(otherUserStatus));

        when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(1L), any(), any())).thenReturn(2L * 60_000L);
        when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(2L), any(), any())).thenReturn(6L * 60_000L);

        when(reviewEventRepository.countDistinctExamAttemptsByUser(1L)).thenReturn(0L);
        when(reviewEventRepository.countDistinctExamAttemptsByUser(2L)).thenReturn(1L);

        when(userAchievementRepository.findByUserId(1L)).thenReturn(List.of());
        when(userAchievementRepository.findByUserId(2L)).thenReturn(List.of(buildAchievement(2L, AchievementCode.SCHOLAR)));

        List<LeaderboardEntryDto> leaderboard = service.getLeaderboard(1L, 5);

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.getFirst().userId()).isEqualTo(2L);
        assertThat(leaderboard.getFirst().rank()).isEqualTo(1);
        assertThat(leaderboard.getFirst().points()).isGreaterThan(leaderboard.get(1).points());

        LeaderboardEntryDto currentUser = leaderboard.stream()
                .filter(LeaderboardEntryDto::currentUser)
                .findFirst()
                .orElseThrow();
        assertThat(currentUser.userId()).isEqualTo(1L);
        assertThat(currentUser.displayName()).isEqualTo("Bạn");
    }

    @Test
    void getLeaderboard_shouldApplyLimitBounds() {
        List<Long> manyUsers = new ArrayList<>();
        for (long i = 1; i <= 40; i++) {
            manyUsers.add(i);
            UserStreakStatus status = new UserStreakStatus();
            status.setUserId(i);
            status.setCurrentStreak(0);
            when(streakStatusRepository.findByUserId(i)).thenReturn(Optional.of(status));
            when(userAchievementRepository.findByUserId(i)).thenReturn(List.of());
        }

        when(reviewEventRepository.findRecentActiveUserIds(90)).thenReturn(manyUsers);
        when(reviewEventRepository.findRecentActiveUserIds(9)).thenReturn(manyUsers);
        when(reviewEventRepository.countDistinctExamAttemptsByUser(any())).thenReturn(0L);

        List<LeaderboardEntryDto> capped = service.getLeaderboard(1L, 100);
        List<LeaderboardEntryDto> floored = service.getLeaderboard(1L, 1);

        assertThat(capped).hasSize(30);
        assertThat(floored).hasSize(3);
        verify(reviewEventRepository).findRecentActiveUserIds(90);
        verify(reviewEventRepository).findRecentActiveUserIds(9);
    }

    private UserAchievement buildAchievement(Long userId, AchievementCode code) {
        UserAchievement achievement = new UserAchievement();
        achievement.setUserId(userId);
        achievement.setAchievementCode(code);
        achievement.setUnlockedAt(Instant.now());
        return achievement;
    }
}
