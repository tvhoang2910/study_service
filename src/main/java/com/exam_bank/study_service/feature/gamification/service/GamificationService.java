package com.exam_bank.study_service.feature.gamification.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.CalendarDayDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.dto.StreakCalendarDto;
import com.exam_bank.study_service.feature.gamification.entity.AchievementCode;
import com.exam_bank.study_service.feature.gamification.entity.UserAchievement;
import com.exam_bank.study_service.feature.gamification.entity.UserStreakStatus;
import com.exam_bank.study_service.feature.gamification.repository.UserAchievementRepository;
import com.exam_bank.study_service.feature.gamification.repository.UserStreakStatusRepository;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DAILY_STREAK_TARGET_MINUTES = 15;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final int SCHOLAR_DAILY_MINUTES = 5;
    private static final double TOP_SCORER_MIN_PERCENT = 80.0;
    private static final double SPEED_DEMON_MIN_PERCENT = 70.0;
    private static final long SPEED_DEMON_MAX_LATENCY_PER_QUESTION_MS = 45_000L;
    private static final int SHARPSHOOTER_STREAK = 3;
    private static final int WEEKEND_WARRIOR_MIN_ATTEMPTS = 1;
    private static final int STREAK_FIRE_MIN_DAYS = 2;
    private static final int BOOKWORM_MIN_EXAMS = 2;
    private static final double PERSISTENT_FAIL_PERCENT = 60.0;
    private static final double PERSISTENT_RECOVERY_PERCENT = 70.0;
    private static final int INSPIRER_MIN_ANSWER_CHANGES = 3;
    private static final int EXPLORER_MIN_EXAMS = 3;
    private static final int DEFAULT_LEADERBOARD_LIMIT = 10;
    private static final int MAX_LEADERBOARD_LIMIT = 30;
        // Temporary compatibility gate for databases that still enforce old enum check constraints.
        private static final Set<AchievementCode> DB_COMPATIBLE_CODES = EnumSet.of(
            AchievementCode.NIGHT_OWL,
            AchievementCode.EXAM_DESTROYER,
            AchievementCode.ANSWER_INSPECTOR);

    private final StudyReviewEventRepository reviewEventRepository;
    private final UserStreakStatusRepository streakStatusRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AuthUserLookupClient authUserLookupClient;

    private static final Map<AchievementCode, AchievementDefinition> ACHIEVEMENTS;
    static {
        Map<AchievementCode, AchievementDefinition> definitions = new EnumMap<>(AchievementCode.class);
        definitions.put(AchievementCode.TOP_SCORER, AchievementDefinition.builder()
            .code(AchievementCode.TOP_SCORER)
            .name("Thủ khoa")
            .description("Đạt từ 8.0 điểm (80%) trong một bài thi bất kỳ.")
            .icon("CROWN")
            .groupName("Học thuật")
            .points(220)
            .build());
        definitions.put(AchievementCode.FIRST_COMPLETION, AchievementDefinition.builder()
            .code(AchievementCode.FIRST_COMPLETION)
            .name("Vượt vũ môn")
            .description("Hoàn thành bài thi đầu tiên trên hệ thống.")
            .icon("FLAG")
            .groupName("Học thuật")
            .points(80)
            .build());
        definitions.put(AchievementCode.SPEED_DEMON, AchievementDefinition.builder()
            .code(AchievementCode.SPEED_DEMON)
            .name("Thần tốc")
            .description("Hoàn thành bài thi đạt từ 7.0 điểm với tốc độ trung bình dưới 45 giây/câu.")
            .icon("ZAP")
            .groupName("Học thuật")
            .points(180)
            .build());
        definitions.put(AchievementCode.SHARPSHOOTER, AchievementDefinition.builder()
            .code(AchievementCode.SHARPSHOOTER)
            .name("Bách phát bách trúng")
            .description("Trả lời đúng liên tiếp 3 câu trong cùng một bài thi.")
            .icon("TARGET")
            .groupName("Học thuật")
            .points(200)
            .build());
        definitions.put(AchievementCode.SCHOLAR, AchievementDefinition.builder()
            .code(AchievementCode.SCHOLAR)
            .name("Học bá")
            .description("Học đủ 5 phút trong ngày (theo thời gian học ghi nhận).")
            .icon("BOOK_OPEN")
            .groupName("Học thuật")
            .points(300)
            .build());

        definitions.put(AchievementCode.NIGHT_GRINDER, AchievementDefinition.builder()
            .code(AchievementCode.NIGHT_GRINDER)
            .name("Cày đêm")
            .description("Hoàn thành bài thi trong khung giờ 00:00 - 04:59.")
            .icon("MOON")
            .groupName("Chuyên cần")
            .points(130)
            .build());
        definitions.put(AchievementCode.WEEKEND_WARRIOR, AchievementDefinition.builder()
            .code(AchievementCode.WEEKEND_WARRIOR)
            .name("Chiến binh cuối tuần")
            .description("Hoàn thành ít nhất 1 bài thi vào Thứ 7 hoặc Chủ nhật.")
            .icon("SWORD")
            .groupName("Chuyên cần")
            .points(160)
            .build());
        definitions.put(AchievementCode.STREAK_FIRE, AchievementDefinition.builder()
            .code(AchievementCode.STREAK_FIRE)
            .name("Lửa cháy")
            .description("Đạt chuỗi 2 ngày học liên tiếp đủ điều kiện streak.")
            .icon("FLAME")
            .groupName("Chuyên cần")
            .points(140)
            .build());
        definitions.put(AchievementCode.BOOKWORM, AchievementDefinition.builder()
            .code(AchievementCode.BOOKWORM)
            .name("Mọt sách")
            .description("Ôn tập qua ít nhất 2 đề thi khác nhau.")
            .icon("LIBRARY")
            .groupName("Chuyên cần")
            .points(170)
            .build());
        definitions.put(AchievementCode.PERSISTENT, AchievementDefinition.builder()
            .code(AchievementCode.PERSISTENT)
            .name("Kiên trì")
            .description("Thi lại một bài từng dưới 6.0 và nâng lên từ 7.0 trở lên.")
            .icon("REFRESH")
            .groupName("Chuyên cần")
            .points(210)
            .build());

        definitions.put(AchievementCode.INSPIRER, AchievementDefinition.builder()
            .code(AchievementCode.INSPIRER)
            .name("Người truyền cảm hứng")
            .description("Trong quá trình ôn tập, đổi đáp án ít nhất 3 lần (thể hiện có phân tích).")
            .icon("HEART")
            .groupName("Cộng đồng")
            .points(240)
            .build());
        definitions.put(AchievementCode.EXPLORER, AchievementDefinition.builder()
            .code(AchievementCode.EXPLORER)
            .name("Nhà thám hiểm")
            .description("Hoàn thành ít nhất 3 đề thi khác nhau.")
            .icon("COMPASS")
            .groupName("Cộng đồng")
            .points(190)
            .build());
        definitions.put(AchievementCode.LEARNING_AMBASSADOR, AchievementDefinition.builder()
            .code(AchievementCode.LEARNING_AMBASSADOR)
            .name("Đại sứ học tập")
            .description("Hoàn thành ít nhất 1 bài thi và chia sẻ thành tựu học tập.")
            .icon("MEGAPHONE")
            .groupName("Cộng đồng")
            .points(200)
            .build());
        ACHIEVEMENTS = Map.copyOf(definitions);
    }

    @Transactional
    public GamificationOverviewDto getOverview(Long userId) {
        Instant now = Instant.now();
        RefreshResult refreshed = refreshProgress(userId, now);
        List<AchievementViewDto> recentUnlocked = userAchievementRepository.findTop5ByUserIdOrderByUnlockedAtDesc(userId)
                .stream()
                .map(this::toAchievementView)
                .toList();

        Map<AchievementCode, UserAchievement> unlockedByCode = dedupeByCode(userAchievementRepository.findByUserId(userId));
        Set<AchievementCode> eligibleCodes = evaluateEligibleCodes(userId, now, refreshed.status(), refreshed.todayStudyMinutes());
        Set<AchievementCode> effectiveUnlockedCodes = EnumSet.noneOf(AchievementCode.class);
        effectiveUnlockedCodes.addAll(unlockedByCode.keySet());
        effectiveUnlockedCodes.addAll(eligibleCodes);

        int achievementPoints = effectiveUnlockedCodes.stream()
            .map(ACHIEVEMENTS::get)
            .filter(def -> def != null)
            .mapToInt(AchievementDefinition::getPoints)
                .sum();
        int streakPoints = safeInt(refreshed.status().getCurrentStreak()) * 5;

        return GamificationOverviewDto.builder()
                .streakDays(safeInt(refreshed.status().getCurrentStreak()))
                .longestStreak(safeInt(refreshed.status().getLongestStreak()))
            .dailyStudyMinutes(refreshed.todayStudyMinutes())
            .dailyTargetMinutes(DAILY_STREAK_TARGET_MINUTES)
                .todayQualified(refreshed.todayQualified())
                .justQualifiedToday(refreshed.justQualifiedToday())
                .points(streakPoints + achievementPoints)
                .newlyUnlockedAchievements(refreshed.newlyUnlocked().stream().map(this::toAchievementView).toList())
                .recentUnlockedAchievements(recentUnlocked)
                .build();
    }

    @Transactional
    public List<AchievementViewDto> getAchievements(Long userId) {
        Instant now = Instant.now();
        RefreshResult refreshed = refreshProgress(userId, now);
        Map<AchievementCode, UserAchievement> unlockedByCode = dedupeByCode(userAchievementRepository.findByUserId(userId));
        Set<AchievementCode> eligibleCodes = evaluateEligibleCodes(userId, now, refreshed.status(), refreshed.todayStudyMinutes());

        return ACHIEVEMENTS.values().stream()
            .sorted(Comparator
                .comparing(AchievementDefinition::getGroupName)
                .thenComparing(AchievementDefinition::getPoints, Comparator.reverseOrder()))
                .map(def -> {
                    UserAchievement unlocked = unlockedByCode.get(def.getCode());
                boolean isUnlocked = unlocked != null || eligibleCodes.contains(def.getCode());
                    return AchievementViewDto.builder()
                            .code(def.getCode())
                            .name(def.getName())
                            .description(def.getDescription())
                            .icon(def.getIcon())
                    .groupName(def.getGroupName())
                            .points(def.getPoints())
                    .unlocked(isUnlocked)
                            .unlockedAt(unlocked != null ? unlocked.getUnlockedAt() : null)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboard(Long currentUserId, Integer requestedLimit) {
        int limit = normalizeLeaderboardLimit(requestedLimit);
        Instant now = Instant.now();

        List<Long> candidateUserIds = new ArrayList<>(reviewEventRepository.findRecentActiveUserIds(limit * 3));
        if (!candidateUserIds.contains(currentUserId)) {
            candidateUserIds.add(currentUserId);
        }
        if (candidateUserIds.isEmpty()) {
            candidateUserIds.add(currentUserId);
        }

        List<Long> distinctCandidateUserIds = candidateUserIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, String> displayNamesByUserId = Optional
            .ofNullable(authUserLookupClient.findDisplayNamesByUserIds(Set.copyOf(distinctCandidateUserIds)))
            .orElse(Map.of());

        List<LeaderboardScoreSnapshot> snapshots = distinctCandidateUserIds.stream()
            .map(userId -> buildLeaderboardSnapshot(userId, currentUserId, now, displayNamesByUserId))
                .sorted((a, b) -> {
                    int byPoints = Integer.compare(b.points(), a.points());
                    if (byPoints != 0) {
                        return byPoints;
                    }
                    int byStreak = Integer.compare(b.streakDays(), a.streakDays());
                    if (byStreak != 0) {
                        return byStreak;
                    }
                    return Long.compare(a.userId(), b.userId());
                })
                .limit(limit)
                .toList();

        List<LeaderboardEntryDto> result = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            LeaderboardScoreSnapshot snapshot = snapshots.get(i);
            result.add(LeaderboardEntryDto.builder()
                    .rank(i + 1)
                    .userId(snapshot.userId())
                    .displayName(snapshot.displayName())
                    .points(snapshot.points())
                    .streakDays(snapshot.streakDays())
                    .unlockedAchievements(snapshot.unlockedAchievements())
                    .currentUser(snapshot.currentUser())
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public StreakCalendarDto getStreakCalendar(Long userId, YearMonth month) {
        Instant monthStart = month.atDay(1).atStartOfDay(APP_ZONE).toInstant();
        Instant monthEndExclusive = month.plusMonths(1).atDay(1).atStartOfDay(APP_ZONE).toInstant();

        Set<LocalDate> activityDates = reviewEventRepository.findActivityDatesByUserBetween(userId, monthStart, monthEndExclusive)
                .stream()
                .collect(Collectors.toSet());
        Set<LocalDate> qualifiedDates = reviewEventRepository.findQualifiedDatesByUserBetween(
                        userId,
                        monthStart,
                        monthEndExclusive,
                DAILY_STREAK_TARGET_MINUTES * MILLIS_PER_MINUTE)
                .stream()
                .collect(Collectors.toSet());

        List<CalendarDayDto> days = new ArrayList<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate current = month.atDay(day);
            days.add(CalendarDayDto.builder()
                    .date(current)
                    .activityCompleted(activityDates.contains(current))
                    .streakQualified(qualifiedDates.contains(current))
                    .build());
        }

        return StreakCalendarDto.builder()
                .month(month.toString())
                .totalDays(month.lengthOfMonth())
                .activityDays(activityDates.size())
                .qualifiedDays(qualifiedDates.size())
                .days(days)
                .build();
    }

    @Transactional
    public void refreshProgressForReview(Long userId, Instant reviewedAt) {
        refreshProgress(userId, reviewedAt != null ? reviewedAt : Instant.now());
    }

    @Transactional
    public void markLearningAmbassadorShared(Long userId) {
        unlockIfAbsent(userId, AchievementCode.LEARNING_AMBASSADOR, Instant.now());
    }

    private RefreshResult refreshProgress(Long userId, Instant referenceInstant) {
        ZonedDateTime nowZoned = referenceInstant.atZone(APP_ZONE);
        LocalDate today = nowZoned.toLocalDate();

        UserStreakStatus status = streakStatusRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserStreakStatus created = new UserStreakStatus();
                    created.setUserId(userId);
                    return created;
                });

        boolean streakChanged = normalizeExpiredStreak(status, today);

        Instant dayStart = today.atStartOfDay(APP_ZONE).toInstant();
        Instant nextDayStart = today.plusDays(1).atStartOfDay(APP_ZONE).toInstant();

        long todayStudyDurationMs = reviewEventRepository.sumStudyDurationMsByUserBetween(userId, dayStart, nextDayStart);
        int todayStudyMinutes = toRoundedMinutes(todayStudyDurationMs);
        boolean todayQualified = todayStudyMinutes >= DAILY_STREAK_TARGET_MINUTES;
        boolean justQualifiedToday = false;

        if (todayQualified) {
            LocalDate lastQualifiedDate = status.getLastQualifiedDate();
            if (!today.equals(lastQualifiedDate)) {
                if (lastQualifiedDate != null && lastQualifiedDate.isEqual(today.minusDays(1))) {
                    status.setCurrentStreak(safeInt(status.getCurrentStreak()) + 1);
                } else {
                    status.setCurrentStreak(1);
                }
                status.setLastQualifiedDate(today);
                status.setStreakUpdatedAt(referenceInstant);
                status.setLongestStreak(Math.max(safeInt(status.getLongestStreak()), safeInt(status.getCurrentStreak())));
                justQualifiedToday = true;
                streakChanged = true;
            }
        }

        if (streakChanged) {
            status = streakStatusRepository.save(status);
        }

        List<UserAchievement> newlyUnlocked = unlockEligibleAchievements(userId, referenceInstant, status, todayStudyMinutes);
        return new RefreshResult(status, todayStudyMinutes, todayQualified, justQualifiedToday, newlyUnlocked);
    }

    private int toRoundedMinutes(long durationMs) {
        if (durationMs <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) durationMs / MILLIS_PER_MINUTE);
    }

    private boolean normalizeExpiredStreak(UserStreakStatus status, LocalDate today) {
        LocalDate lastQualifiedDate = status.getLastQualifiedDate();
        if (lastQualifiedDate == null) {
            return false;
        }

        if (lastQualifiedDate.isBefore(today.minusDays(1)) && safeInt(status.getCurrentStreak()) > 0) {
            status.setCurrentStreak(0);
            return true;
        }

        return false;
    }

    private List<UserAchievement> unlockEligibleAchievements(
            Long userId,
            Instant referenceInstant,
            UserStreakStatus status,
            int todayStudyMinutes) {
        List<UserAchievement> unlocked = new ArrayList<>();
        Set<AchievementCode> eligibleCodes = evaluateEligibleCodes(userId, referenceInstant, status, todayStudyMinutes);

        if (eligibleCodes.contains(AchievementCode.TOP_SCORER)) {
            unlockIfAbsent(userId, AchievementCode.TOP_SCORER, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.FIRST_COMPLETION)) {
            unlockIfAbsent(userId, AchievementCode.FIRST_COMPLETION, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.SPEED_DEMON)) {
            unlockIfAbsent(userId, AchievementCode.SPEED_DEMON, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.SHARPSHOOTER)) {
            unlockIfAbsent(userId, AchievementCode.SHARPSHOOTER, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.SCHOLAR)) {
            unlockIfAbsent(userId, AchievementCode.SCHOLAR, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.NIGHT_GRINDER)) {
            unlockIfAbsent(userId, AchievementCode.NIGHT_GRINDER, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.WEEKEND_WARRIOR)) {
            unlockIfAbsent(userId, AchievementCode.WEEKEND_WARRIOR, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.STREAK_FIRE)) {
            unlockIfAbsent(userId, AchievementCode.STREAK_FIRE, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.BOOKWORM)) {
            unlockIfAbsent(userId, AchievementCode.BOOKWORM, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.PERSISTENT)) {
            unlockIfAbsent(userId, AchievementCode.PERSISTENT, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.INSPIRER)) {
            unlockIfAbsent(userId, AchievementCode.INSPIRER, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.EXPLORER)) {
            unlockIfAbsent(userId, AchievementCode.EXPLORER, referenceInstant).ifPresent(unlocked::add);
        }
        if (eligibleCodes.contains(AchievementCode.LEARNING_AMBASSADOR)) {
            unlockIfAbsent(userId, AchievementCode.LEARNING_AMBASSADOR, referenceInstant).ifPresent(unlocked::add);
        }

        return unlocked;
    }

    private Set<AchievementCode> evaluateEligibleCodes(
            Long userId,
            Instant referenceInstant,
            UserStreakStatus status,
            int todayStudyMinutes) {
        Set<AchievementCode> eligible = EnumSet.noneOf(AchievementCode.class);

        if (isTopScorerEligible(userId)) {
            eligible.add(AchievementCode.TOP_SCORER);
        }
        if (isFirstCompletionEligible(userId)) {
            eligible.add(AchievementCode.FIRST_COMPLETION);
        }
        if (isSpeedDemonEligible(userId)) {
            eligible.add(AchievementCode.SPEED_DEMON);
        }
        if (isSharpshooterEligible(userId)) {
            eligible.add(AchievementCode.SHARPSHOOTER);
        }
        if (isScholarEligible(todayStudyMinutes)) {
            eligible.add(AchievementCode.SCHOLAR);
        }
        if (isNightGrinderEligible(userId)) {
            eligible.add(AchievementCode.NIGHT_GRINDER);
        }
        if (isWeekendWarriorEligible(userId)) {
            eligible.add(AchievementCode.WEEKEND_WARRIOR);
        }
        if (isStreakFireEligible(status)) {
            eligible.add(AchievementCode.STREAK_FIRE);
        }
        if (isBookwormEligible(userId)) {
            eligible.add(AchievementCode.BOOKWORM);
        }
        if (isPersistentEligible(userId)) {
            eligible.add(AchievementCode.PERSISTENT);
        }
        if (isInspirerEligible(userId)) {
            eligible.add(AchievementCode.INSPIRER);
        }
        if (isExplorerEligible(userId)) {
            eligible.add(AchievementCode.EXPLORER);
        }
        if (isLearningAmbassadorEligible(userId)) {
            eligible.add(AchievementCode.LEARNING_AMBASSADOR);
        }

        return eligible;
    }

    private boolean isTopScorerEligible(Long userId) {
        return reviewEventRepository.countAttemptByUserWithMinScore(userId, TOP_SCORER_MIN_PERCENT) > 0;
    }

    private boolean isFirstCompletionEligible(Long userId) {
        return reviewEventRepository.countDistinctExamAttemptsByUser(userId) > 0;
    }

    private boolean isSpeedDemonEligible(Long userId) {
        return reviewEventRepository.countFastHighScoreAttempts(
                userId,
                SPEED_DEMON_MIN_PERCENT,
                SPEED_DEMON_MAX_LATENCY_PER_QUESTION_MS) > 0;
    }

    private boolean isSharpshooterEligible(Long userId) {
        return reviewEventRepository.countAttemptsHavingCorrectAnswerStreak(userId, SHARPSHOOTER_STREAK) > 0;
    }

    private boolean isScholarEligible(int todayStudyMinutes) {
        return todayStudyMinutes >= SCHOLAR_DAILY_MINUTES;
    }

    private boolean isNightGrinderEligible(Long userId) {
        return reviewEventRepository.countNightOwlReviewsByUser(userId) > 0;
    }

    private boolean isWeekendWarriorEligible(Long userId) {
        return reviewEventRepository.countWeekendAttemptsByUser(userId) >= WEEKEND_WARRIOR_MIN_ATTEMPTS;
    }

    private boolean isStreakFireEligible(UserStreakStatus status) {
        return safeInt(status.getCurrentStreak()) >= STREAK_FIRE_MIN_DAYS;
    }

    private boolean isBookwormEligible(Long userId) {
        return reviewEventRepository.countDistinctExamByUser(userId) >= BOOKWORM_MIN_EXAMS;
    }

    private boolean isPersistentEligible(Long userId) {
        return reviewEventRepository.countRetakeImprovementExams(userId, PERSISTENT_FAIL_PERCENT, PERSISTENT_RECOVERY_PERCENT) > 0;
    }

    private boolean isInspirerEligible(Long userId) {
        return reviewEventRepository.sumAnswerChangesByUser(userId) >= INSPIRER_MIN_ANSWER_CHANGES;
    }

    private boolean isExplorerEligible(Long userId) {
        return reviewEventRepository.countDistinctExamByUser(userId) >= EXPLORER_MIN_EXAMS;
    }

    private boolean isLearningAmbassadorEligible(Long userId) {
        return reviewEventRepository.countDistinctExamAttemptsByUser(userId) >= 1;
    }

    private Optional<UserAchievement> unlockIfAbsent(Long userId, AchievementCode code, Instant unlockedAt) {
        if (!DB_COMPATIBLE_CODES.contains(code)) {
            return Optional.empty();
        }

        if (!userAchievementRepository.findAllByUserIdAndAchievementCode(userId, code).isEmpty()) {
            return Optional.empty();
        }

        UserAchievement achievement = new UserAchievement();
        achievement.setUserId(userId);
        achievement.setAchievementCode(code);
        achievement.setUnlockedAt(unlockedAt);
        try {
            return Optional.of(userAchievementRepository.save(achievement));
        } catch (DataIntegrityViolationException ex) {
            log.warn("Skip unlock achievement due to DB constraint mismatch: userId={}, code={}", userId, code);
            return Optional.empty();
        }
    }

    private LeaderboardScoreSnapshot buildLeaderboardSnapshot(
            Long userId,
            Long currentUserId,
            Instant referenceInstant,
            Map<Long, String> displayNamesByUserId) {
        ZonedDateTime nowZoned = referenceInstant.atZone(APP_ZONE);
        LocalDate today = nowZoned.toLocalDate();

        UserStreakStatus rawStatus = streakStatusRepository.findByUserId(userId).orElseGet(() -> {
            UserStreakStatus created = new UserStreakStatus();
            created.setUserId(userId);
            return created;
        });

        UserStreakStatus effectiveStatus = cloneStatus(rawStatus);
        normalizeExpiredStreak(effectiveStatus, today);

        Instant dayStart = today.atStartOfDay(APP_ZONE).toInstant();
        Instant nextDayStart = today.plusDays(1).atStartOfDay(APP_ZONE).toInstant();
        long todayStudyDurationMs = reviewEventRepository.sumStudyDurationMsByUserBetween(userId, dayStart, nextDayStart);
        int todayStudyMinutes = toRoundedMinutes(todayStudyDurationMs);

        Map<AchievementCode, UserAchievement> unlockedByCode = dedupeByCode(userAchievementRepository.findByUserId(userId));
        Set<AchievementCode> eligibleCodes = evaluateEligibleCodes(userId, referenceInstant, effectiveStatus, todayStudyMinutes);
        Set<AchievementCode> effectiveUnlockedCodes = EnumSet.noneOf(AchievementCode.class);
        effectiveUnlockedCodes.addAll(unlockedByCode.keySet());
        effectiveUnlockedCodes.addAll(eligibleCodes);

        int achievementPoints = effectiveUnlockedCodes.stream()
                .map(ACHIEVEMENTS::get)
                .filter(def -> def != null)
                .mapToInt(AchievementDefinition::getPoints)
                .sum();
        int streakDays = safeInt(effectiveStatus.getCurrentStreak());
        int points = achievementPoints + (streakDays * 5);

        boolean isCurrentUser = userId.equals(currentUserId);
        String resolvedDisplayName = normalizeDisplayName(displayNamesByUserId.get(userId));
        String displayName;
        if (isCurrentUser) {
            displayName = "Bạn";
        } else if (resolvedDisplayName != null) {
            displayName = resolvedDisplayName;
        } else {
            displayName = "Thành viên";
        }

        return new LeaderboardScoreSnapshot(
                userId,
                displayName,
                points,
                streakDays,
                effectiveUnlockedCodes.size(),
                isCurrentUser);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        String normalized = displayName.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private UserStreakStatus cloneStatus(UserStreakStatus source) {
        UserStreakStatus cloned = new UserStreakStatus();
        cloned.setUserId(source.getUserId());
        cloned.setCurrentStreak(source.getCurrentStreak());
        cloned.setLongestStreak(source.getLongestStreak());
        cloned.setLastQualifiedDate(source.getLastQualifiedDate());
        cloned.setStreakUpdatedAt(source.getStreakUpdatedAt());
        return cloned;
    }

    private int normalizeLeaderboardLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LEADERBOARD_LIMIT;
        }
        return Math.min(Math.max(requestedLimit, 3), MAX_LEADERBOARD_LIMIT);
    }

    private AchievementViewDto toAchievementView(UserAchievement achievement) {
        AchievementDefinition definition = ACHIEVEMENTS.get(achievement.getAchievementCode());
        if (definition == null) {
            return AchievementViewDto.builder()
                    .code(achievement.getAchievementCode())
                    .name(achievement.getAchievementCode().name())
                    .description("Thành tựu bí ẩn")
                    .icon("BADGE")
                    .groupName("Khác")
                    .points(0)
                    .unlocked(true)
                    .unlockedAt(achievement.getUnlockedAt())
                    .build();
        }

        return AchievementViewDto.builder()
                .code(definition.getCode())
                .name(definition.getName())
                .description(definition.getDescription())
                .icon(definition.getIcon())
                .groupName(definition.getGroupName())
                .points(definition.getPoints())
                .unlocked(true)
                .unlockedAt(achievement.getUnlockedAt())
                .build();
    }

    private Map<AchievementCode, UserAchievement> dedupeByCode(List<UserAchievement> achievements) {
        return achievements.stream().collect(Collectors.toMap(
                UserAchievement::getAchievementCode,
                Function.identity(),
                (left, right) -> {
                    Instant leftUnlockedAt = left.getUnlockedAt();
                    Instant rightUnlockedAt = right.getUnlockedAt();
                    if (leftUnlockedAt == null) {
                        return right;
                    }
                    if (rightUnlockedAt == null) {
                        return left;
                    }
                    return rightUnlockedAt.isAfter(leftUnlockedAt) ? right : left;
                }));
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private record RefreshResult(
            UserStreakStatus status,
            int todayStudyMinutes,
            boolean todayQualified,
            boolean justQualifiedToday,
            List<UserAchievement> newlyUnlocked) {
    }

        private record LeaderboardScoreSnapshot(
            Long userId,
            String displayName,
            int points,
            int streakDays,
            int unlockedAchievements,
            boolean currentUser) {
        }

    @Getter
    @Builder
    private static class AchievementDefinition {
        private AchievementCode code;
        private String name;
        private String description;
        private String icon;
        private String groupName;
        private Integer points;
    }
}
