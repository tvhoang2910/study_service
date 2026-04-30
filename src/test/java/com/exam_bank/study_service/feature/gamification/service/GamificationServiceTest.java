package com.exam_bank.study_service.feature.gamification.service;

import static com.exam_bank.study_service.shared.AppConstants.APP_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.feature.gamification.dto.AdminAchievementUpsertRequestDto;
import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.CalendarDayDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.entity.AchievementDefinition;
import com.exam_bank.study_service.feature.gamification.entity.UserAchievement;
import com.exam_bank.study_service.feature.gamification.entity.UserStreakStatus;
import com.exam_bank.study_service.feature.gamification.repository.AchievementDefinitionRepository;
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

        @Mock
        private AchievementDefinitionRepository achievementDefinitionRepository;

        @Mock
        private AuthUserLookupClient authUserLookupClient;

        @Mock
        private GamificationNotificationPublisher gamificationNotificationPublisher;

        @InjectMocks
        private GamificationService service;

        @Test
        void getAchievements_shouldUnlockDynamicDailyHoursDefinition_whenStudyMinutesReachThreshold() {
                Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
                AchievementDefinition persistent = buildDefinition(
                                "PERSISTENT",
                                "Kiên trì",
                                "Học hơn 2 tiếng trong 1 ngày",
                                "REFRESH",
                                "Chuyên cần",
                                210,
                                null,
                                createdAt);
                persistent.setRuleType("CUMULATIVE_STUDY_MINUTES");
                persistent.setRuleThreshold(120);
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(persistent));

                when(streakStatusRepository.findByUserId(9L)).thenReturn(Optional.of(buildStatus(9L, 0, 0, null)));
                when(streakStatusRepository.save(any(UserStreakStatus.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(9L), any(), any()))
                                .thenReturn(120L * 60_000L);
                when(userAchievementRepository.findByUserId(9L)).thenReturn(List.of());

                List<AchievementViewDto> achievements = service.getAchievements(9L);

                assertThat(achievements).hasSize(1);
                assertThat(achievements.getFirst().code()).isEqualTo("PERSISTENT");
                assertThat(achievements.getFirst().unlocked()).isTrue();
        }

        @Test
        void getAchievements_shouldKeepDynamicDailyHoursDefinitionLocked_whenStudyMinutesUnderThreshold() {
                Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
                AchievementDefinition persistent = buildDefinition(
                                "PERSISTENT",
                                "Kiên trì",
                                "Học hơn 2 tiếng trong 1 ngày",
                                "REFRESH",
                                "Chuyên cần",
                                210,
                                null,
                                createdAt);
                persistent.setRuleType("CUMULATIVE_STUDY_MINUTES");
                persistent.setRuleThreshold(120);
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(persistent));

                when(streakStatusRepository.findByUserId(9L)).thenReturn(Optional.of(buildStatus(9L, 0, 0, null)));
                when(streakStatusRepository.save(any(UserStreakStatus.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(9L), any(), any()))
                                .thenReturn(119L * 60_000L);
                when(userAchievementRepository.findByUserId(9L)).thenReturn(List.of());

                List<AchievementViewDto> achievements = service.getAchievements(9L);

                assertThat(achievements).hasSize(1);
                assertThat(achievements.getFirst().code()).isEqualTo("PERSISTENT");
                assertThat(achievements.getFirst().unlocked()).isFalse();
        }

        @Test
        void getAchievements_shouldNotTreatLegacyUnlockAsEffective_whenUnlockedBeforeDefinitionCreatedAt() {
                Instant definitionCreatedAt = Instant.parse("2026-04-10T10:00:00Z");
                Instant legacyUnlockedAt = Instant.parse("2026-04-09T10:00:00Z");

                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(
                                                buildDefinition(
                                                                "PERSISTENT",
                                                                "Kiên trì",
                                                                "Tiến bộ mỗi ngày",
                                                                "REFRESH",
                                                                "Chuyên cần",
                                                                210,
                                                                null,
                                                                definitionCreatedAt)));

                when(streakStatusRepository.findByUserId(15L)).thenReturn(Optional.of(buildStatus(15L, 0, 0, null)));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(15L), any(), any())).thenReturn(0L);
                when(userAchievementRepository.findByUserId(15L)).thenReturn(List.of(
                                buildAchievement(15L, "PERSISTENT", legacyUnlockedAt)));

                List<AchievementViewDto> achievements = service.getAchievements(15L);

                assertThat(achievements).hasSize(1);
                assertThat(achievements.getFirst().code()).isEqualTo("PERSISTENT");
                assertThat(achievements.getFirst().unlocked()).isFalse();
        }

        @Test
        void getAchievements_shouldPreferLatestUnlockTimestamp_whenDuplicateAchievementExists() {
                Instant definitionCreatedAt = Instant.parse("2026-04-01T00:00:00Z");
                Instant older = Instant.parse("2026-04-09T12:00:00Z");
                Instant newer = Instant.parse("2026-04-10T12:00:00Z");

                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(
                                                buildDefinition(
                                                                "SCHOLAR",
                                                                "Học bá",
                                                                "Học đủ 5 phút",
                                                                "BOOK_OPEN",
                                                                "Học thuật",
                                                                300,
                                                                null,
                                                                definitionCreatedAt)));

                when(streakStatusRepository.findByUserId(40L)).thenReturn(Optional.of(buildStatus(40L, 0, 0, null)));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(40L), any(), any())).thenReturn(0L);
                when(userAchievementRepository.findByUserId(40L)).thenReturn(List.of(
                                buildAchievement(40L, "SCHOLAR", older),
                                buildAchievement(40L, "SCHOLAR", newer)));

                List<AchievementViewDto> achievements = service.getAchievements(40L);

                assertThat(achievements).hasSize(1);
                assertThat(achievements.getFirst().unlocked()).isTrue();
                assertThat(achievements.getFirst().unlockedAt()).isEqualTo(newer);
        }

        @Test
        void getOverview_shouldCalculatePointsFromEffectiveUnlocksAndStreak_withoutPersistingAchievementRows() {
                Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
                AchievementDefinition persistent = buildDefinition(
                                "PERSISTENT",
                                "Kiên trì",
                                "Học hơn 2 tiếng trong 1 ngày",
                                "REFRESH",
                                "Chuyên cần",
                                210,
                                null,
                                createdAt);
                persistent.setRuleType("CUMULATIVE_STUDY_MINUTES");
                persistent.setRuleThreshold(120);
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(persistent));

                LocalDate today = LocalDate.now(APP_ZONE);
                when(streakStatusRepository.findByUserId(22L)).thenReturn(Optional.of(buildStatus(22L, 2, 2, today)));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(22L), any(), any()))
                                .thenReturn(120L * 60_000L);
                when(userAchievementRepository.findByUserId(22L)).thenReturn(List.of());

                GamificationOverviewDto overview = service.getOverview(22L);

                assertThat(overview.dailyStudyMinutes()).isEqualTo(120);
                assertThat(overview.points()).isEqualTo(220);
                assertThat(overview.todayQualified()).isTrue();
                assertThat(overview.justQualifiedToday()).isFalse();

                verify(userAchievementRepository, never()).save(any());
        }

        @Test
        void getOverview_shouldResetExpiredStreak_whenGapExceedsOneDay() {
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of());

                LocalDate today = LocalDate.now(APP_ZONE);
                UserStreakStatus status = buildStatus(30L, 5, 9, today.minusDays(3));

                when(streakStatusRepository.findByUserId(30L)).thenReturn(Optional.of(status));
                when(streakStatusRepository.save(any(UserStreakStatus.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(30L), any(), any())).thenReturn(0L);
                when(userAchievementRepository.findByUserId(30L)).thenReturn(List.of());

                GamificationOverviewDto overview = service.getOverview(30L);

                assertThat(overview.streakDays()).isEqualTo(0);
                assertThat(overview.longestStreak()).isEqualTo(9);
                assertThat(overview.todayQualified()).isFalse();
                assertThat(overview.justQualifiedToday()).isFalse();
                verify(streakStatusRepository).save(any(UserStreakStatus.class));
        }

        @Test
        void getLeaderboard_shouldRankUsersByPoints_andIncludeCurrentUserFlag() {
                Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(
                                                buildDefinition(
                                                                "PERSISTENT",
                                                                "Kiên trì",
                                                                "Học hơn 2 tiếng trong 1 ngày",
                                                                "REFRESH",
                                                                "Chuyên cần",
                                                                210,
                                                                null,
                                                                createdAt)));

                when(reviewEventRepository.findRecentActiveUserIds(15)).thenReturn(List.of(2L, 1L));
                when(streakStatusRepository.findByUserId(1L)).thenReturn(Optional.of(buildStatus(1L, 1, 1, null)));
                when(streakStatusRepository.findByUserId(2L)).thenReturn(Optional.of(buildStatus(2L, 4, 4, null)));

                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(1L), any(), any()))
                                .thenReturn(2L * 60_000L);
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(2L), any(), any()))
                                .thenReturn(130L * 60_000L);

                when(userAchievementRepository.findByUserId(1L)).thenReturn(List.of());
                when(userAchievementRepository.findByUserId(2L)).thenReturn(List.of());
                when(authUserLookupClient.findDisplayNamesByUserIds(any()))
                                .thenReturn(Map.of(1L, "Nguyễn Văn A", 2L, "Nguyễn Văn B"));

                List<LeaderboardEntryDto> leaderboard = service.getLeaderboard(1L, 5);

                assertThat(leaderboard).hasSize(2);
                assertThat(leaderboard.getFirst().userId()).isEqualTo(2L);
                assertThat(leaderboard.getFirst().rank()).isEqualTo(1);
                assertThat(leaderboard.getFirst().points()).isGreaterThan(leaderboard.get(1).points());
                assertThat(leaderboard.getFirst().displayName()).isEqualTo("Nguyễn Văn B");

                LeaderboardEntryDto currentUser = leaderboard.stream()
                                .filter(LeaderboardEntryDto::currentUser)
                                .findFirst()
                                .orElseThrow();
                assertThat(currentUser.userId()).isEqualTo(1L);
                assertThat(currentUser.displayName()).isEqualTo("Bạn (Nguyễn Văn A)");
        }

        @Test
        void getLeaderboard_shouldApplyLimitBounds() {
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of());
                List<Long> manyUsers = new ArrayList<>();
                for (long i = 1; i <= 40; i++) {
                        manyUsers.add(i);
                }

                when(streakStatusRepository.findByUserId(anyLong())).thenAnswer(invocation -> {
                        Long userId = invocation.getArgument(0);
                        return Optional.of(buildStatus(userId, 0, 0, null));
                });
                when(userAchievementRepository.findByUserId(anyLong())).thenReturn(List.of());
                when(reviewEventRepository.findRecentActiveUserIds(90)).thenReturn(manyUsers);
                when(reviewEventRepository.findRecentActiveUserIds(9)).thenReturn(manyUsers);
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(anyLong(), any(), any())).thenReturn(0L);
                when(authUserLookupClient.findDisplayNamesByUserIds(any())).thenReturn(Map.of());

                List<LeaderboardEntryDto> capped = service.getLeaderboard(1L, 100);
                List<LeaderboardEntryDto> floored = service.getLeaderboard(1L, 1);

                assertThat(capped).hasSize(30);
                assertThat(floored).hasSize(3);
                verify(reviewEventRepository).findRecentActiveUserIds(90);
                verify(reviewEventRepository).findRecentActiveUserIds(9);
        }

        @Test
        void getOverview_shouldIncreaseStreak_whenTodayJustQualifiedAfterYesterday() {
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of());
                LocalDate today = LocalDate.now(APP_ZONE);
                UserStreakStatus status = buildStatus(31L, 1, 1, today.minusDays(1));

                when(streakStatusRepository.findByUserId(31L)).thenReturn(Optional.of(status));
                when(streakStatusRepository.save(any(UserStreakStatus.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(31L), any(), any()))
                                .thenReturn(15L * 60_000L);
                when(userAchievementRepository.findByUserId(31L)).thenReturn(List.of());

                GamificationOverviewDto overview = service.getOverview(31L);

                assertThat(overview.streakDays()).isEqualTo(2);
                assertThat(overview.longestStreak()).isEqualTo(2);
                assertThat(overview.todayQualified()).isTrue();
                assertThat(overview.justQualifiedToday()).isTrue();
                verify(streakStatusRepository).save(any(UserStreakStatus.class));
        }

        @Test
        void getOverview_shouldNotDoubleCountStreak_whenAlreadyQualifiedToday() {
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of());
                LocalDate today = LocalDate.now(APP_ZONE);
                UserStreakStatus status = buildStatus(32L, 3, 7, today);

                when(streakStatusRepository.findByUserId(32L)).thenReturn(Optional.of(status));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(32L), any(), any()))
                                .thenReturn(20L * 60_000L);
                when(userAchievementRepository.findByUserId(32L)).thenReturn(List.of());

                GamificationOverviewDto overview = service.getOverview(32L);

                assertThat(overview.streakDays()).isEqualTo(3);
                assertThat(overview.longestStreak()).isEqualTo(7);
                assertThat(overview.todayQualified()).isTrue();
                assertThat(overview.justQualifiedToday()).isFalse();
                verify(streakStatusRepository, never()).save(any(UserStreakStatus.class));
        }

        @Test
        void getLeaderboard_shouldIncludeCurrentUser_evenWhenNotPresentInRecentActiveList() {
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of());
                UserStreakStatus currentUserStatus = buildStatus(1L, 0, 0, null);
                UserStreakStatus activeUserStatus = buildStatus(2L, 0, 0, null);

                when(reviewEventRepository.findRecentActiveUserIds(15)).thenReturn(List.of(2L));
                when(streakStatusRepository.findByUserId(1L)).thenReturn(Optional.of(currentUserStatus));
                when(streakStatusRepository.findByUserId(2L)).thenReturn(Optional.of(activeUserStatus));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(1L), any(), any())).thenReturn(0L);
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(2L), any(), any())).thenReturn(0L);
                when(userAchievementRepository.findByUserId(1L)).thenReturn(List.of());
                when(userAchievementRepository.findByUserId(2L)).thenReturn(List.of());
                when(authUserLookupClient.findDisplayNamesByUserIds(any())).thenReturn(Map.of());

                List<LeaderboardEntryDto> leaderboard = service.getLeaderboard(1L, 5);

                assertThat(leaderboard).hasSize(2);
                assertThat(leaderboard.stream().anyMatch(item -> item.userId().equals(1L) && item.currentUser()))
                                .isTrue();
        }

        @Test
        void getLeaderboard_shouldUseDefaultLimit_whenRequestedLimitIsNull() {
                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of());
                List<Long> manyUsers = new ArrayList<>();
                for (long i = 1; i <= 50; i++) {
                        manyUsers.add(i);
                }

                when(reviewEventRepository.findRecentActiveUserIds(30)).thenReturn(manyUsers);
                when(streakStatusRepository.findByUserId(any())).thenAnswer(invocation -> {
                        Long userId = invocation.getArgument(0);
                        return Optional.of(buildStatus(userId, 0, 0, null));
                });
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(any(), any(), any())).thenReturn(0L);
                when(userAchievementRepository.findByUserId(any())).thenReturn(List.of());
                when(authUserLookupClient.findDisplayNamesByUserIds(any())).thenReturn(Map.of());

                List<LeaderboardEntryDto> leaderboard = service.getLeaderboard(1L, null);

                assertThat(leaderboard).hasSize(10);
                verify(reviewEventRepository).findRecentActiveUserIds(30);
        }

        @Test
        void getStreakCalendar_shouldComputeMonthlyCalendarCountersCorrectly() {
                YearMonth targetMonth = YearMonth.of(2026, 4);
                LocalDate day1 = LocalDate.of(2026, 4, 2);
                LocalDate day2 = LocalDate.of(2026, 4, 10);

                when(reviewEventRepository.findActivityDatesByUserBetween(eq(55L), any(), any()))
                                .thenReturn(List.of(day1, day2));
                when(reviewEventRepository.findQualifiedDatesByUserBetween(eq(55L), any(), any(), anyLong()))
                                .thenReturn(List.of(day2));

                var calendar = service.getStreakCalendar(55L, targetMonth);

                assertThat(calendar.month()).isEqualTo("2026-04");
                assertThat(calendar.totalDays()).isEqualTo(30);
                assertThat(calendar.activityDays()).isEqualTo(2);
                assertThat(calendar.qualifiedDays()).isEqualTo(1);

                CalendarDayDto day1Dto = calendar.days().stream()
                                .filter(item -> item.date().equals(day1))
                                .findFirst()
                                .orElseThrow();
                CalendarDayDto day2Dto = calendar.days().stream()
                                .filter(item -> item.date().equals(day2))
                                .findFirst()
                                .orElseThrow();

                assertThat(day1Dto.activityCompleted()).isTrue();
                assertThat(day1Dto.streakQualified()).isFalse();
                assertThat(day2Dto.activityCompleted()).isTrue();
                assertThat(day2Dto.streakQualified()).isTrue();
        }

        @Test
        void markLearningAmbassadorShared_shouldUnlockAchievement() {
                when(userAchievementRepository.findByUserIdAndAchievementCode(77L, "LEARNING_AMBASSADOR"))
                                .thenReturn(Optional.empty());

                service.markLearningAmbassadorShared(77L);

                verify(userAchievementRepository).findByUserIdAndAchievementCode(77L, "LEARNING_AMBASSADOR");
                verify(userAchievementRepository).save(any(UserAchievement.class));
        }

        @Test
        void markLearningAmbassadorShared_shouldSkipPersist_whenCodeBlockedByLegacyConstraint() {
                when(userAchievementRepository.existsIncompatibleAchievementCodeConstraint("LEARNING_AMBASSADOR"))
                                .thenReturn(true);

                service.markLearningAmbassadorShared(77L);

                verify(userAchievementRepository, never()).findByUserIdAndAchievementCode(anyLong(), any());
                verify(userAchievementRepository, never()).save(any(UserAchievement.class));
        }

        @Test
        void markLearningAmbassadorShared_shouldNotSaveWhenAlreadyUnlocked() {
                when(userAchievementRepository.findByUserIdAndAchievementCode(78L, "LEARNING_AMBASSADOR"))
                                .thenReturn(Optional.of(buildAchievement(78L, "LEARNING_AMBASSADOR", Instant.now())));

                service.markLearningAmbassadorShared(78L);

                verify(userAchievementRepository).findByUserIdAndAchievementCode(78L, "LEARNING_AMBASSADOR");
                verify(userAchievementRepository, never()).save(any(UserAchievement.class));
        }

        @Test
        void unlockAchievementsForReview_shouldPublishAchievementNotification_whenNewAchievementUnlocked() {
                Instant now = Instant.parse("2026-04-14T12:00:00Z");

                AchievementDefinition definition = buildDefinition(
                                "CUMULATIVE_EXAM_ATTEMPTS_1",
                                "Hoàn thành 1 bài",
                                "Mở khóa khi hoàn thành 1 bài",
                                "TROPHY",
                                "Tích lũy",
                                50,
                                null,
                                Instant.parse("2026-04-01T00:00:00Z"));
                definition.setRuleType("CUMULATIVE_EXAM_ATTEMPTS");
                definition.setRuleThreshold(1);

                when(achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc())
                                .thenReturn(List.of(definition));
                when(streakStatusRepository.findByUserId(91L)).thenReturn(Optional.of(buildStatus(91L, 0, 0, null)));
                when(reviewEventRepository.sumStudyDurationMsByUserBetween(eq(91L), any(), any())).thenReturn(0L);
                when(reviewEventRepository.countDistinctExamAttemptsByUser(91L)).thenReturn(1L);
                when(userAchievementRepository.findByUserIdAndAchievementCode(91L, "CUMULATIVE_EXAM_ATTEMPTS_1"))
                                .thenReturn(Optional.empty());
                when(userAchievementRepository.save(any(UserAchievement.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                service.unlockAchievementsForReview(91L, now);

                verify(gamificationNotificationPublisher)
                                .publishAchievementUnlocked(91L, List.of("Hoàn thành 1 bài"));
                verify(gamificationNotificationPublisher, never()).publishStreakQualified(anyLong(), anyInt(), anyInt());
        }

        @Test
        void upsertAchievementDefinition_shouldPersistNormalizedCode_whenPayloadValid() {
                AdminAchievementUpsertRequestDto request = new AdminAchievementUpsertRequestDto(
                                "  cumulative_exam_attempts_5 ",
                                "Hoàn thành 5 bài",
                                "Mở khóa khi làm đủ 5 bài",
                                "TROPHY",
                                "Tích lũy",
                                120,
                                true,
                                null,
                                "cumulative_exam_attempts",
                                5,
                                null,
                                null);

                when(achievementDefinitionRepository.findByCode("CUMULATIVE_EXAM_ATTEMPTS_5"))
                                .thenReturn(Optional.empty());
                when(achievementDefinitionRepository.save(any(AchievementDefinition.class))).thenAnswer(invocation -> {
                        AchievementDefinition saved = invocation.getArgument(0);
                        saved.setId(99L);
                        return saved;
                });

                var result = service.upsertAchievementDefinition(request);

                assertThat(result.code()).isEqualTo("CUMULATIVE_EXAM_ATTEMPTS_5");
                assertThat(result.ruleType()).isEqualTo("CUMULATIVE_EXAM_ATTEMPTS");
                assertThat(result.ruleThreshold()).isEqualTo(5);
                assertThat(result.active()).isTrue();
                verify(achievementDefinitionRepository).save(any(AchievementDefinition.class));
        }

        @Test
        void upsertAchievementDefinition_shouldRejectLegacyAutoUnlockRule() {
                AdminAchievementUpsertRequestDto request = new AdminAchievementUpsertRequestDto(
                                "CUMULATIVE_EXAM_ATTEMPTS_5",
                                "Hoàn thành 5 bài",
                                "Mở khóa khi làm đủ 5 bài",
                                "TROPHY",
                                "Tích lũy",
                                120,
                                true,
                                "SCHOLAR",
                                "CUMULATIVE_EXAM_ATTEMPTS",
                                5,
                                null,
                                null);

                assertThatThrownBy(() -> service.upsertAchievementDefinition(request))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("Legacy autoUnlockRule is no longer supported");

                verify(achievementDefinitionRepository, never()).save(any(AchievementDefinition.class));
        }

        @Test
        void upsertAchievementDefinition_shouldRejectCompoundRule_whenClauseCountTooSmall() {
                AdminAchievementUpsertRequestDto request = new AdminAchievementUpsertRequestDto(
                                "COMPOUND_ONLY_ONE",
                                "Rule kết hợp lỗi",
                                "Chỉ có một clause",
                                "SHIELD",
                                "Kết hợp",
                                200,
                                true,
                                null,
                                "COMPOUND_RULE",
                                null,
                                null,
                                "{\"logic\":\"AND\",\"clauses\":[{\"ruleType\":\"STREAK_DAYS\",\"threshold\":3}]}");

                assertThatThrownBy(() -> service.upsertAchievementDefinition(request))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("COMPOUND_RULE requires at least 2 clauses");

                verify(achievementDefinitionRepository, never()).save(any(AchievementDefinition.class));
        }

        @Test
        void deleteAchievementDefinition_shouldThrow_whenCodeNotFound() {
                when(achievementDefinitionRepository.findByCode("NOT_FOUND")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.deleteAchievementDefinition("NOT_FOUND"))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("Achievement not found");

                verify(achievementDefinitionRepository, never()).delete(any(AchievementDefinition.class));
        }

        @Test
        void assignAchievementToUser_shouldThrow_whenUserIdNotPositive() {
                assertThatThrownBy(() -> service.assignAchievementToUser("STREAK_DAYS_5", 0L))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("userId must be a positive number");

                verify(achievementDefinitionRepository, never()).findByCode(any());
        }

        @Test
        void assignAchievementToUser_shouldThrow_whenDefinitionInactive() {
                AchievementDefinition inactive = buildDefinition(
                                "STREAK_DAYS_5",
                                "Giữ nhịp học",
                                "Streak 5 ngày",
                                "FLAME",
                                "Chuỗi",
                                160,
                                null,
                                Instant.now());
                inactive.setActive(false);
                when(achievementDefinitionRepository.findByCode("STREAK_DAYS_5")).thenReturn(Optional.of(inactive));

                assertThatThrownBy(() -> service.assignAchievementToUser("STREAK_DAYS_5", 55L))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("Cannot assign an inactive achievement");

                verify(userAchievementRepository, never()).save(any(UserAchievement.class));
        }

        @Test
        void assignAchievementToUser_shouldPersistUnlock_whenDefinitionActiveAndNotYetUnlocked() {
                AchievementDefinition active = buildDefinition(
                                "STREAK_DAYS_5",
                                "Giữ nhịp học",
                                "Streak 5 ngày",
                                "FLAME",
                                "Chuỗi",
                                160,
                                null,
                                Instant.now());
                active.setActive(true);
                when(achievementDefinitionRepository.findByCode("STREAK_DAYS_5")).thenReturn(Optional.of(active));
                when(userAchievementRepository.findByUserIdAndAchievementCode(55L, "STREAK_DAYS_5"))
                                .thenReturn(Optional.empty());
                when(userAchievementRepository.save(any(UserAchievement.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                service.assignAchievementToUser("STREAK_DAYS_5", 55L);

                verify(userAchievementRepository).save(any(UserAchievement.class));
        }

        private UserStreakStatus buildStatus(Long userId, int currentStreak, int longestStreak,
                        LocalDate lastQualifiedDate) {
                UserStreakStatus status = new UserStreakStatus();
                status.setUserId(userId);
                status.setCurrentStreak(currentStreak);
                status.setLongestStreak(longestStreak);
                status.setLastQualifiedDate(lastQualifiedDate);
                return status;
        }

        private UserAchievement buildAchievement(Long userId, String code, Instant unlockedAt) {
                UserAchievement achievement = new UserAchievement();
                achievement.setUserId(userId);
                achievement.setAchievementCode(code);
                achievement.setUnlockedAt(unlockedAt);
                return achievement;
        }

        private AchievementDefinition buildDefinition(
                        String code,
                        String name,
                        String description,
                        String icon,
                        String groupName,
                        int points,
                        String autoUnlockRule,
                        Instant createdAt) {
                AchievementDefinition definition = new AchievementDefinition();
                definition.setCode(code);
                definition.setName(name);
                definition.setDescription(description);
                definition.setIcon(icon);
                definition.setGroupName(groupName);
                definition.setPoints(points);
                definition.setActive(true);
                definition.setAutoUnlockRule(autoUnlockRule);
                definition.setCreatedAt(createdAt);
                return definition;
        }
}
