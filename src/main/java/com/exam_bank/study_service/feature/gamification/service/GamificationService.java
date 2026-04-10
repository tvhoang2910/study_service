package com.exam_bank.study_service.feature.gamification.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.feature.gamification.dto.AchievementDefinitionDto;
import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.AdminAchievementUpsertRequestDto;
import com.exam_bank.study_service.feature.gamification.dto.CalendarDayDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.dto.StreakCalendarDto;
import com.exam_bank.study_service.feature.gamification.entity.AchievementDefinition;
import com.exam_bank.study_service.feature.gamification.entity.UserAchievement;
import com.exam_bank.study_service.feature.gamification.entity.UserStreakStatus;
import com.exam_bank.study_service.feature.gamification.repository.AchievementDefinitionRepository;
import com.exam_bank.study_service.feature.gamification.repository.UserAchievementRepository;
import com.exam_bank.study_service.feature.gamification.repository.UserStreakStatusRepository;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;

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
                private static final Pattern DAILY_HOURS_PATTERN = Pattern
                    .compile("(?i)h[oọ]c.*?(?:h[oơ]n|tr[eê]n|i[tí]t\s+nh[aấ]t)?\\s*(\\d+)\\s*(?:ti[eế]ng|gi[oờ]).*(?:m[oỗ]i|trong)\\s*1\\s*ng[aà]y");
                private static final Pattern DAILY_MINUTES_PATTERN = Pattern
                    .compile("(?i)h[oọ]c.*?(?:h[oơ]n|tr[eê]n|i[tí]t\s+nh[aấ]t)?\\s*(\\d+)\\s*ph[uú]t.*(?:m[oỗ]i|trong)\\s*1\\s*ng[aà]y");

    private static final String RULE_TOP_SCORER = "TOP_SCORER";
    private static final String RULE_FIRST_COMPLETION = "FIRST_COMPLETION";
    private static final String RULE_SPEED_DEMON = "SPEED_DEMON";
    private static final String RULE_SHARPSHOOTER = "SHARPSHOOTER";
    private static final String RULE_SCHOLAR = "SCHOLAR";
    private static final String RULE_NIGHT_GRINDER = "NIGHT_GRINDER";
    private static final String RULE_WEEKEND_WARRIOR = "WEEKEND_WARRIOR";
    private static final String RULE_STREAK_FIRE = "STREAK_FIRE";
    private static final String RULE_BOOKWORM = "BOOKWORM";
    private static final String RULE_PERSISTENT = "PERSISTENT";
    private static final String RULE_INSPIRER = "INSPIRER";
    private static final String RULE_EXPLORER = "EXPLORER";
    private static final String RULE_LEARNING_AMBASSADOR = "LEARNING_AMBASSADOR";

    private static final String RULE_TYPE_DAILY_STUDY_MINUTES = "DAILY_STUDY_MINUTES";
    private static final String RULE_TYPE_HIGH_SCORE_ATTEMPTS = "HIGH_SCORE_ATTEMPTS";
    private static final String RULE_TYPE_DISTINCT_EXAMS = "DISTINCT_EXAMS";
    private static final String RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS = "CUMULATIVE_EXAM_ATTEMPTS";
    private static final String RULE_TYPE_CUMULATIVE_STUDY_MINUTES = "CUMULATIVE_STUDY_MINUTES";
    private static final String RULE_TYPE_STREAK_DAYS = "STREAK_DAYS";
    private static final String RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS = "QUALITY_MIN_SCORE_ATTEMPTS";
    private static final String RULE_TYPE_COMPOUND = "COMPOUND_RULE";
    private static final String LOGIC_AND = "AND";
    private static final String LOGIC_OR = "OR";

        private static final Set<String> RULE_TYPES_FOR_FOUR_GROUPS = Set.of(
            RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS,
            RULE_TYPE_CUMULATIVE_STUDY_MINUTES,
            RULE_TYPE_STREAK_DAYS,
            RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS,
            RULE_TYPE_COMPOUND);

        private static final Map<String, String> LEGACY_CODE_TO_NEW_CODE = Map.ofEntries(
            Map.entry(RULE_FIRST_COMPLETION, "CUMULATIVE_EXAM_ATTEMPTS_3"),
            Map.entry(RULE_SCHOLAR, "CUMULATIVE_STUDY_MINUTES_60"),
            Map.entry(RULE_STREAK_FIRE, "STREAK_DAYS_5"),
            Map.entry(RULE_TOP_SCORER, "QUALITY_MIN_SCORE_85_X1"),
            Map.entry(RULE_BOOKWORM, "CUMULATIVE_EXAM_ATTEMPTS_10"),
            Map.entry(RULE_EXPLORER, "STREAK_DAYS_14")
        );

        private static final Set<String> LEGACY_CODES_TO_DROP = Set.of(
            RULE_SPEED_DEMON,
            RULE_SHARPSHOOTER,
            RULE_NIGHT_GRINDER,
            RULE_WEEKEND_WARRIOR,
            RULE_PERSISTENT,
            RULE_INSPIRER,
            RULE_LEARNING_AMBASSADOR,
            "NIGHT_OWL",
            "EXAM_DESTROYER",
            "ANSWER_INSPECTOR");

    private static final Set<String> SUPPORTED_LEGACY_RULES = Set.of(
            RULE_TOP_SCORER,
            RULE_FIRST_COMPLETION,
            RULE_SPEED_DEMON,
            RULE_SHARPSHOOTER,
            RULE_SCHOLAR,
            RULE_NIGHT_GRINDER,
            RULE_WEEKEND_WARRIOR,
            RULE_STREAK_FIRE,
            RULE_BOOKWORM,
            RULE_PERSISTENT,
            RULE_INSPIRER,
            RULE_EXPLORER,
            RULE_LEARNING_AMBASSADOR);

    private final StudyReviewEventRepository reviewEventRepository;
    private final UserStreakStatusRepository streakStatusRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementDefinitionRepository achievementDefinitionRepository;
    private final AuthUserLookupClient authUserLookupClient;
    private final AtomicBoolean legacyDefinitionsMigrated = new AtomicBoolean(false);

        private record DefaultAchievementSpec(
            String code,
            String name,
            String description,
            String icon,
            String groupName,
            int points,
            String ruleType,
            Integer ruleThreshold,
            Integer ruleThresholdSecondary,
            String ruleConfigJson) {
        }

        private static final List<DefaultAchievementSpec> DEFAULT_ACHIEVEMENTS = List.of(
            new DefaultAchievementSpec(
                "CUMULATIVE_EXAM_ATTEMPTS_3",
                "Khoi dong thi cu",
                "Hoan thanh 3 bai thi.",
                "FILE_CHECK",
                "Tich luy",
                90,
                RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS,
                3,
                null,
                null),
            new DefaultAchievementSpec(
                "CUMULATIVE_EXAM_ATTEMPTS_10",
                "Ben bi luyen tap",
                "Hoan thanh 10 bai thi.",
                "BOOK_CHECK",
                "Tich luy",
                180,
                RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS,
                10,
                null,
                null),
            new DefaultAchievementSpec(
                "CUMULATIVE_STUDY_MINUTES_60",
                "Nap nang luong",
                "Hoc du 60 phut trong ngay.",
                "CLOCK_3",
                "Tich luy",
                100,
                RULE_TYPE_CUMULATIVE_STUDY_MINUTES,
                60,
                null,
                null),
            new DefaultAchievementSpec(
                "CUMULATIVE_STUDY_MINUTES_180",
                "Co may hoc tap",
                "Hoc du 180 phut trong ngay.",
                "TIMER",
                "Tich luy",
                260,
                RULE_TYPE_CUMULATIVE_STUDY_MINUTES,
                180,
                null,
                null),
            new DefaultAchievementSpec(
                "STREAK_DAYS_5",
                "Giu nhip hoc",
                "Duy tri streak 5 ngay lien tiep.",
                "FLAME",
                "Chuoi",
                160,
                RULE_TYPE_STREAK_DAYS,
                5,
                null,
                null),
            new DefaultAchievementSpec(
                "STREAK_DAYS_14",
                "Ky luat thep",
                "Duy tri streak 14 ngay lien tiep.",
                "TORCH",
                "Chuoi",
                340,
                RULE_TYPE_STREAK_DAYS,
                14,
                null,
                null),
            new DefaultAchievementSpec(
                "QUALITY_MIN_SCORE_85_X1",
                "Danh dau xuat sac",
                "Dat tu 85 phan tram it nhat 1 lan.",
                "AWARD",
                "Chat luong",
                150,
                RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS,
                85,
                1,
                null),
            new DefaultAchievementSpec(
                "QUALITY_MIN_SCORE_90_X3",
                "Phong do cao",
                "Dat tu 90 phan tram it nhat 3 lan.",
                "CROWN",
                "Chat luong",
                320,
                RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS,
                90,
                3,
                null),
            new DefaultAchievementSpec(
                "COMPOUND_AND_STUDY_SCORE",
                "Toan tam toan luc",
                "Hoc du 120 phut va dat tu 85 phan tram it nhat 1 lan.",
                "SHIELD_CHECK",
                "Ket hop",
                400,
                RULE_TYPE_COMPOUND,
                null,
                null,
                "{\"logic\":\"AND\",\"clauses\":[{\"ruleType\":\"CUMULATIVE_STUDY_MINUTES\",\"threshold\":120},{\"ruleType\":\"QUALITY_MIN_SCORE_ATTEMPTS\",\"threshold\":85,\"thresholdSecondary\":1}]}"),
            new DefaultAchievementSpec(
                "COMPOUND_OR_STREAK_QUALITY",
                "Bung no nang luc",
                "Streak tu 10 ngay hoac dat tu 90 phan tram it nhat 2 lan.",
                "SPARKLES",
                "Ket hop",
                420,
                RULE_TYPE_COMPOUND,
                null,
                null,
                "{\"logic\":\"OR\",\"clauses\":[{\"ruleType\":\"STREAK_DAYS\",\"threshold\":10},{\"ruleType\":\"QUALITY_MIN_SCORE_ATTEMPTS\",\"threshold\":90,\"thresholdSecondary\":2}]}")
        );

    @Transactional
    public GamificationOverviewDto getOverview(Long userId) {
        Instant now = Instant.now();
        RefreshResult refreshed = refreshProgress(userId, now, false);
        List<AchievementDefinition> definitions = getActiveDefinitions();
        Map<String, AchievementDefinition> definitionByCode = toDefinitionMap(definitions);

        List<AchievementViewDto> recentUnlocked = userAchievementRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(UserAchievement::getUnlockedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed())
            .filter(achievement -> isEffectiveUnlock(achievement,
                definitionByCode.get(achievement.getAchievementCode())))
            .limit(5)
                .map(achievement -> toAchievementView(achievement, definitionByCode))
                .toList();

        Map<String, UserAchievement> unlockedByCode = dedupeByCode(userAchievementRepository.findByUserId(userId));
        Set<String> autoUnlockedCodes = resolveAutoUnlockedCodes(definitions, userId, refreshed.status(),
            refreshed.todayStudyMinutes());
        Set<String> effectiveUnlockedCodes = unlockedByCode.entrySet().stream()
            .filter(entry -> isEffectiveUnlock(entry.getValue(), definitionByCode.get(entry.getKey())))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        effectiveUnlockedCodes.addAll(autoUnlockedCodes);

        int achievementPoints = effectiveUnlockedCodes.stream()
                .map(definitionByCode::get)
                .filter(Objects::nonNull)
                .mapToInt(def -> safeInt(def.getPoints()))
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
                .newlyUnlockedAchievements(refreshed.newlyUnlocked().stream()
                        .map(achievement -> toAchievementView(achievement, definitionByCode))
                        .toList())
                .recentUnlockedAchievements(recentUnlocked)
                .build();
    }

    @Transactional
    public List<AchievementViewDto> getAchievements(Long userId) {
        RefreshResult refreshed = refreshProgress(userId, Instant.now(), false);
        List<AchievementDefinition> definitions = getActiveDefinitions();
        Map<String, AchievementDefinition> definitionByCode = toDefinitionMap(definitions);

        Map<String, UserAchievement> unlockedByCode = dedupeByCode(userAchievementRepository.findByUserId(userId));
        Set<String> autoUnlockedCodes = resolveAutoUnlockedCodes(definitions, userId, refreshed.status(),
            refreshed.todayStudyMinutes());

        return definitions.stream()
                .map(def -> {
                    UserAchievement unlocked = unlockedByCode.get(def.getCode());
                    boolean isUnlocked = isEffectiveUnlock(unlocked, definitionByCode.get(def.getCode()))
                        || autoUnlockedCodes.contains(def.getCode());
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

        List<AchievementDefinition> definitions = getActiveDefinitions();
        Map<String, AchievementDefinition> definitionByCode = toDefinitionMap(definitions);

        List<LeaderboardScoreSnapshot> snapshots = distinctCandidateUserIds.stream()
                .map(userId -> buildLeaderboardSnapshot(userId, currentUserId, now, displayNamesByUserId, definitions,
                        definitionByCode))
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
        refreshProgress(userId, reviewedAt != null ? reviewedAt : Instant.now(), false);
    }

    @Transactional
    public void markLearningAmbassadorShared(Long userId) {
        unlockIfAbsent(userId, RULE_LEARNING_AMBASSADOR, Instant.now());
    }

    @Transactional
    public List<AchievementDefinitionDto> getAchievementDefinitionsForAdmin() {
        ensureLegacyDefinitionsMigrated();
        return achievementDefinitionRepository.findAllByOrderByGroupNameAscPointsDesc().stream()
                .map(this::toDefinitionDto)
                .toList();
    }

    @Transactional
    public AchievementDefinitionDto upsertAchievementDefinition(AdminAchievementUpsertRequestDto request) {
        String normalizedCode = normalizeCode(request.code());
        String normalizedRule = normalizeRule(request.autoUnlockRule());
        String normalizedRuleType = normalizeRuleType(request.ruleType());
        String normalizedRuleConfigJson = normalizeRuleConfigJson(request.ruleConfigJson());

        validateRuleBinding(normalizedRuleType, normalizedRule);
        validateParameterizedRule(normalizedRuleType, request.ruleThreshold(), request.ruleThresholdSecondary(),
            normalizedRuleConfigJson);

        AchievementDefinition definition = achievementDefinitionRepository.findByCode(normalizedCode)
                .orElseGet(AchievementDefinition::new);

        definition.setCode(normalizedCode);
        definition.setName(request.name().trim());
        definition.setDescription(request.description().trim());
        definition.setIcon(request.icon().trim());
        definition.setGroupName(request.groupName().trim());
        definition.setPoints(request.points());
        definition.setActive(Boolean.TRUE.equals(request.active()));
        definition.setAutoUnlockRule(normalizedRule);
        definition.setRuleType(normalizedRuleType);
        definition.setRuleThreshold(request.ruleThreshold());
        definition.setRuleThresholdSecondary(request.ruleThresholdSecondary());
        definition.setRuleConfigJson(normalizedRuleConfigJson);

        return toDefinitionDto(achievementDefinitionRepository.save(definition));
    }

    @Transactional
    public void deleteAchievementDefinition(String code) {
        AchievementDefinition definition = achievementDefinitionRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Achievement not found"));
        achievementDefinitionRepository.delete(definition);
    }

    @Transactional
    public void assignAchievementToUser(String code, Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "userId must be a positive number");
        }

        String normalizedCode = normalizeCode(code);
        AchievementDefinition definition = achievementDefinitionRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Achievement not found"));
        if (!Boolean.TRUE.equals(definition.getActive())) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot assign an inactive achievement");
        }

        unlockIfAbsent(userId, normalizedCode, Instant.now());
    }

    private RefreshResult refreshProgress(Long userId, Instant referenceInstant, boolean persistAchievementUnlocks) {
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

        List<UserAchievement> newlyUnlocked = persistAchievementUnlocks
            ? unlockEligibleAchievements(userId, referenceInstant, status, todayStudyMinutes)
            : List.of();
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
        List<AchievementDefinition> definitions = getActiveDefinitions();
        List<UserAchievement> unlocked = new ArrayList<>();
        for (AchievementDefinition definition : definitions) {
            String ruleType = normalizeRuleType(definition.getRuleType());
            if (ruleType == null
                    || !isParameterizedRuleEligible(definition, ruleType, userId, status, todayStudyMinutes)) {
                continue;
            }
            unlockIfAbsent(userId, definition.getCode(), referenceInstant).ifPresent(unlocked::add);
        }
        return unlocked;
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

    private Optional<UserAchievement> unlockIfAbsent(Long userId, String code, Instant unlockedAt) {
        if (userAchievementRepository.findByUserIdAndAchievementCode(userId, code).isPresent()) {
            return Optional.empty();
        }

        UserAchievement achievement = new UserAchievement();
        achievement.setUserId(userId);
        achievement.setAchievementCode(code);
        achievement.setUnlockedAt(unlockedAt);
        try {
            return Optional.ofNullable(userAchievementRepository.save(achievement));
        } catch (DataIntegrityViolationException ex) {
            // Some deployments may still keep old DB check constraints for legacy enum codes.
            log.warn("Skip unlocking incompatible achievement code due to DB constraint: userId={}, code={}", userId,
                    code);
            return Optional.empty();
        }
    }

    private LeaderboardScoreSnapshot buildLeaderboardSnapshot(
            Long userId,
            Long currentUserId,
            Instant referenceInstant,
            Map<Long, String> displayNamesByUserId,
            List<AchievementDefinition> definitions,
            Map<String, AchievementDefinition> definitionByCode) {
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

        Map<String, UserAchievement> unlockedByCode = dedupeByCode(userAchievementRepository.findByUserId(userId));
        Set<String> autoUnlockedCodes = resolveAutoUnlockedCodes(definitions, userId, effectiveStatus,
            todayStudyMinutes);

        Set<String> effectiveUnlockedCodes = unlockedByCode.entrySet().stream()
            .filter(entry -> isEffectiveUnlock(entry.getValue(), definitionByCode.get(entry.getKey())))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        effectiveUnlockedCodes.addAll(autoUnlockedCodes);

        int achievementPoints = effectiveUnlockedCodes.stream()
                .map(definitionByCode::get)
                .filter(Objects::nonNull)
                .mapToInt(def -> safeInt(def.getPoints()))
                .sum();

        int unlockedAchievements = (int) effectiveUnlockedCodes.stream()
            .filter(definitionByCode::containsKey)
            .count();

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
            unlockedAchievements,
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

    private AchievementViewDto toAchievementView(UserAchievement achievement, Map<String, AchievementDefinition> definitionByCode) {
        AchievementDefinition definition = definitionByCode.get(achievement.getAchievementCode());
        if (definition == null) {
            return AchievementViewDto.builder()
                    .code(achievement.getAchievementCode())
                    .name(achievement.getAchievementCode())
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

    private Map<String, UserAchievement> dedupeByCode(List<UserAchievement> achievements) {
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

    private boolean isEffectiveUnlock(UserAchievement achievement, AchievementDefinition definition) {
        if (achievement == null || definition == null) {
            return false;
        }

        Instant unlockedAt = achievement.getUnlockedAt();
        if (unlockedAt == null) {
            return false;
        }

        Instant definitionCreatedAt = definition.getCreatedAt();
        if (definitionCreatedAt == null) {
            return true;
        }

        return !unlockedAt.isBefore(definitionCreatedAt);
    }

    private List<AchievementDefinition> getActiveDefinitions() {
        ensureLegacyDefinitionsMigrated();
        return achievementDefinitionRepository.findAllByActiveTrueOrderByGroupNameAscPointsDesc();
    }

    private void ensureLegacyDefinitionsMigrated() {
        if (!legacyDefinitionsMigrated.compareAndSet(false, true)) {
            return;
        }

        List<AchievementDefinition> definitions = Optional.ofNullable(achievementDefinitionRepository.findAll())
                .orElse(List.of());

        for (AchievementDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }

            if (migrateLegacyRuleType(definition)) {
                achievementDefinitionRepository.save(definition);
                continue;
            }

            if (hasNewRuleType(definition)) {
                continue;
            }

            String legacyRule = normalizeRule(definition.getAutoUnlockRule());
            if (legacyRule == null) {
                achievementDefinitionRepository.delete(definition);
                continue;
            }

            boolean migrated = migrateLegacyRule(definition, legacyRule);
            if (!migrated) {
                achievementDefinitionRepository.delete(definition);
                continue;
            }

            definition.setAutoUnlockRule(null);
            definition.setRuleConfigJson(null);
            achievementDefinitionRepository.save(definition);
        }

        ensureDefaultAchievementsExist();
        migrateLegacyCodesAndPruneUnsupported();
    }

    private void ensureDefaultAchievementsExist() {
        for (DefaultAchievementSpec spec : DEFAULT_ACHIEVEMENTS) {
            if (achievementDefinitionRepository.findByCode(spec.code()).isPresent()) {
                continue;
            }

            AchievementDefinition definition = new AchievementDefinition();
            definition.setCode(spec.code());
            definition.setName(spec.name());
            definition.setDescription(spec.description());
            definition.setIcon(spec.icon());
            definition.setGroupName(spec.groupName());
            definition.setPoints(spec.points());
            definition.setActive(true);
            definition.setAutoUnlockRule(null);
            definition.setRuleType(spec.ruleType());
            definition.setRuleThreshold(spec.ruleThreshold());
            definition.setRuleThresholdSecondary(spec.ruleThresholdSecondary());
            definition.setRuleConfigJson(spec.ruleConfigJson());
            achievementDefinitionRepository.save(definition);
        }
    }

    private boolean hasNewRuleType(AchievementDefinition definition) {
        String normalized = normalizeRuleType(definition.getRuleType());
        if (normalized == null) {
            return false;
        }

        return switch (normalized) {
            case RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS,
                    RULE_TYPE_CUMULATIVE_STUDY_MINUTES,
                    RULE_TYPE_STREAK_DAYS,
                    RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS,
                    RULE_TYPE_COMPOUND -> true;
            default -> false;
        };
    }

    private void migrateLegacyCodesAndPruneUnsupported() {
        List<AchievementDefinition> definitions = Optional.ofNullable(achievementDefinitionRepository.findAll())
                .orElse(List.of());

        for (AchievementDefinition definition : definitions) {
            if (definition == null || definition.getCode() == null) {
                continue;
            }

            String code = definition.getCode().trim().toUpperCase();
            String targetCode = LEGACY_CODE_TO_NEW_CODE.get(code);
            if (targetCode != null) {
                migrateUserAchievementsCode(code, targetCode);
                achievementDefinitionRepository.delete(definition);
                continue;
            }

            if (LEGACY_CODES_TO_DROP.contains(code)) {
                deleteDefinitionAndUserAchievements(definition);
                continue;
            }

            String ruleType = normalizeRuleType(definition.getRuleType());
            if (ruleType == null || !RULE_TYPES_FOR_FOUR_GROUPS.contains(ruleType)) {
                deleteDefinitionAndUserAchievements(definition);
            }
        }
    }

    private void deleteDefinitionAndUserAchievements(AchievementDefinition definition) {
        if (definition == null || definition.getCode() == null) {
            return;
        }

        String code = definition.getCode().trim().toUpperCase();
        List<UserAchievement> affectedAchievements = userAchievementRepository.findByAchievementCode(code);
        if (!affectedAchievements.isEmpty()) {
            userAchievementRepository.deleteAll(affectedAchievements);
        }
        achievementDefinitionRepository.delete(definition);
    }

    private void migrateUserAchievementsCode(String oldCode, String newCode) {
        List<UserAchievement> oldAchievements = userAchievementRepository.findByAchievementCode(oldCode);
        if (oldAchievements.isEmpty()) {
            return;
        }

        for (UserAchievement oldAchievement : oldAchievements) {
            Optional<UserAchievement> existingTarget = userAchievementRepository
                    .findByUserIdAndAchievementCode(oldAchievement.getUserId(), newCode);

            if (existingTarget.isPresent()) {
                UserAchievement targetAchievement = existingTarget.get();
                Instant oldUnlockedAt = oldAchievement.getUnlockedAt();
                Instant targetUnlockedAt = targetAchievement.getUnlockedAt();
                if (targetUnlockedAt == null || (oldUnlockedAt != null && oldUnlockedAt.isBefore(targetUnlockedAt))) {
                    targetAchievement.setUnlockedAt(oldUnlockedAt);
                    userAchievementRepository.save(targetAchievement);
                }
                userAchievementRepository.delete(oldAchievement);
                continue;
            }

            oldAchievement.setAchievementCode(newCode);
            userAchievementRepository.save(oldAchievement);
        }
    }

    private boolean migrateLegacyRuleType(AchievementDefinition definition) {
        String normalized = normalizeRuleType(definition.getRuleType());
        if (normalized == null) {
            return false;
        }

        switch (normalized) {
            case RULE_TYPE_DAILY_STUDY_MINUTES -> {
                definition.setRuleType(RULE_TYPE_CUMULATIVE_STUDY_MINUTES);
                if (definition.getRuleThreshold() == null || definition.getRuleThreshold() <= 0) {
                    definition.setRuleThreshold(SCHOLAR_DAILY_MINUTES);
                }
                definition.setRuleThresholdSecondary(null);
                definition.setRuleConfigJson(null);
                return true;
            }
            case RULE_TYPE_HIGH_SCORE_ATTEMPTS -> {
                definition.setRuleType(RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS);
                if (definition.getRuleThreshold() == null) {
                    definition.setRuleThreshold((int) TOP_SCORER_MIN_PERCENT);
                }
                if (definition.getRuleThresholdSecondary() == null || definition.getRuleThresholdSecondary() <= 0) {
                    definition.setRuleThresholdSecondary(1);
                }
                definition.setRuleConfigJson(null);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean migrateLegacyRule(AchievementDefinition definition, String legacyRule) {
        return switch (legacyRule) {
            case RULE_FIRST_COMPLETION -> {
                definition.setRuleType(RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS);
                definition.setRuleThreshold(1);
                definition.setRuleThresholdSecondary(null);
                yield true;
            }
            case RULE_SCHOLAR -> {
                definition.setRuleType(RULE_TYPE_CUMULATIVE_STUDY_MINUTES);
                definition.setRuleThreshold(SCHOLAR_DAILY_MINUTES);
                definition.setRuleThresholdSecondary(null);
                yield true;
            }
            case RULE_STREAK_FIRE -> {
                definition.setRuleType(RULE_TYPE_STREAK_DAYS);
                definition.setRuleThreshold(STREAK_FIRE_MIN_DAYS);
                definition.setRuleThresholdSecondary(null);
                yield true;
            }
            case RULE_TOP_SCORER -> {
                definition.setRuleType(RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS);
                definition.setRuleThreshold((int) TOP_SCORER_MIN_PERCENT);
                definition.setRuleThresholdSecondary(1);
                yield true;
            }
            case RULE_BOOKWORM -> {
                definition.setRuleType(RULE_TYPE_DISTINCT_EXAMS);
                definition.setRuleThreshold(BOOKWORM_MIN_EXAMS);
                definition.setRuleThresholdSecondary(null);
                yield true;
            }
            case RULE_EXPLORER -> {
                definition.setRuleType(RULE_TYPE_DISTINCT_EXAMS);
                definition.setRuleThreshold(EXPLORER_MIN_EXAMS);
                definition.setRuleThresholdSecondary(null);
                yield true;
            }
            case RULE_LEARNING_AMBASSADOR -> {
                definition.setRuleType(RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS);
                definition.setRuleThreshold(1);
                definition.setRuleThresholdSecondary(null);
                yield true;
            }
            default -> false;
        };
    }

    private Map<String, AchievementDefinition> toDefinitionMap(List<AchievementDefinition> definitions) {
        return definitions.stream().collect(Collectors.toMap(
                AchievementDefinition::getCode,
                Function.identity(),
                (left, right) -> right));
    }

    private Set<String> resolveAutoUnlockedCodes(
            List<AchievementDefinition> definitions,
            Long userId,
            UserStreakStatus status,
            int todayStudyMinutes) {
        Set<String> unlocked = new HashSet<>();
        for (AchievementDefinition definition : definitions) {
            if (isDefinitionEligible(definition, userId, status, todayStudyMinutes)) {
                unlocked.add(definition.getCode());
            }
        }
        return unlocked;
    }

    private boolean isDefinitionEligible(
            AchievementDefinition definition,
            Long userId,
            UserStreakStatus status,
            int todayStudyMinutes) {
        String normalizedRuleType = normalizeRuleType(definition.getRuleType());
        if (normalizedRuleType != null) {
            return isParameterizedRuleEligible(definition, normalizedRuleType, userId, status, todayStudyMinutes);
        }
        return false;
    }

    private boolean isParameterizedRuleEligible(
            AchievementDefinition definition,
            String normalizedRuleType,
            Long userId,
            UserStreakStatus status,
            int todayStudyMinutes) {
        Integer threshold = definition.getRuleThreshold();
        Integer thresholdSecondary = definition.getRuleThresholdSecondary();

        return switch (normalizedRuleType) {
            case RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS -> threshold != null
                && reviewEventRepository.countDistinctExamAttemptsByUser(userId) >= threshold;
            case RULE_TYPE_CUMULATIVE_STUDY_MINUTES -> threshold != null && todayStudyMinutes >= threshold;
            case RULE_TYPE_STREAK_DAYS -> threshold != null && safeInt(status.getCurrentStreak()) >= threshold;
            case RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS -> threshold != null
                && thresholdSecondary != null
                && reviewEventRepository.countAttemptByUserWithMinScore(userId, threshold.doubleValue()) >= thresholdSecondary;
            case RULE_TYPE_COMPOUND -> evaluateCompoundRule(definition.getRuleConfigJson(), userId, status, todayStudyMinutes);
            default -> {
                log.warn("Unsupported parameterized ruleType on definition: code={}, ruleType={}", definition.getCode(),
                        normalizedRuleType);
                yield false;
            }
        };
    }

    private boolean evaluateCompoundRule(String ruleConfigJson, Long userId, UserStreakStatus status, int todayStudyMinutes) {
        CompoundRuleConfig config = parseCompoundRuleConfig(ruleConfigJson);
        if (config == null || config.clauses() == null || config.clauses().isEmpty()) {
            return false;
        }

        String logic = Optional.ofNullable(config.logic()).map(String::trim).map(String::toUpperCase).orElse(LOGIC_AND);
        List<Boolean> results = new ArrayList<>();
        for (CompoundRuleClause clause : config.clauses()) {
            if (clause == null) {
                continue;
            }

            String clauseRuleType = normalizeRuleType(clause.ruleType());
            if (clauseRuleType == null || RULE_TYPE_COMPOUND.equals(clauseRuleType)) {
                results.add(false);
                continue;
            }

            AchievementDefinition virtualDefinition = new AchievementDefinition();
            virtualDefinition.setCode("VIRTUAL");
            virtualDefinition.setRuleType(clauseRuleType);
            virtualDefinition.setRuleThreshold(clause.threshold());
            virtualDefinition.setRuleThresholdSecondary(clause.thresholdSecondary());
            results.add(isParameterizedRuleEligible(virtualDefinition, clauseRuleType, userId, status, todayStudyMinutes));
        }

        if (results.isEmpty()) {
            return false;
        }
        if (LOGIC_OR.equals(logic)) {
            return results.stream().anyMatch(Boolean.TRUE::equals);
        }
        return results.stream().allMatch(Boolean.TRUE::equals);
    }

    private CompoundRuleConfig parseCompoundRuleConfig(String ruleConfigJson) {
        if (ruleConfigJson == null || ruleConfigJson.isBlank()) {
            return null;
        }
        try {
            String compact = ruleConfigJson.replaceAll("\\s+", "");

            String logic = extractStringValue(compact, "logic");
            if (logic == null) {
                throw new IllegalArgumentException("Missing logic");
            }

            int clausesKeyIndex = compact.indexOf("\"clauses\":[");
            if (clausesKeyIndex < 0) {
                throw new IllegalArgumentException("Missing clauses");
            }

            int start = compact.indexOf('[', clausesKeyIndex);
            int end = compact.indexOf(']', start);
            if (start < 0 || end < 0 || end <= start) {
                throw new IllegalArgumentException("Invalid clauses array");
            }

            String clausesRaw = compact.substring(start + 1, end);
            if (clausesRaw.isBlank()) {
                return new CompoundRuleConfig(logic, List.of());
            }

            List<CompoundRuleClause> clauses = new ArrayList<>();
            String normalized = clausesRaw;
            if (normalized.startsWith("{")) {
                normalized = normalized.substring(1);
            }
            if (normalized.endsWith("}")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            String[] parts = normalized.split("\\},\\{");
            for (String part : parts) {
                String clauseJson = "{" + part + "}";
                String ruleType = extractStringValue(clauseJson, "ruleType");
                Integer threshold = extractIntegerValue(clauseJson, "threshold");
                Integer thresholdSecondary = extractIntegerValue(clauseJson, "thresholdSecondary");
                clauses.add(new CompoundRuleClause(ruleType, threshold, thresholdSecondary));
            }

            return new CompoundRuleConfig(logic, clauses);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid ruleConfigJson for COMPOUND_RULE");
        }
    }

    private String extractStringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\":\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Integer extractIntegerValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\":(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    private AchievementDefinitionDto toDefinitionDto(AchievementDefinition definition) {
        return AchievementDefinitionDto.builder()
                .code(definition.getCode())
                .name(definition.getName())
                .description(definition.getDescription())
                .icon(definition.getIcon())
                .groupName(definition.getGroupName())
                .points(definition.getPoints())
                .active(definition.getActive())
                .autoUnlockRule(definition.getAutoUnlockRule())
                .ruleType(definition.getRuleType())
                .ruleThreshold(definition.getRuleThreshold())
                .ruleThresholdSecondary(definition.getRuleThresholdSecondary())
                .ruleConfigJson(definition.getRuleConfigJson())
                .build();
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null) {
            throw new ResponseStatusException(BAD_REQUEST, "code is required");
        }

        String normalized = rawCode.trim().toUpperCase();
        if (normalized.isBlank() || !normalized.matches("^[A-Z0-9_]+$")) {
            throw new ResponseStatusException(BAD_REQUEST, "code must match [A-Z0-9_]+");
        }

        return normalized;
    }

    private String normalizeRule(String rawRule) {
        if (rawRule == null) {
            return null;
        }

        String normalized = rawRule.trim().toUpperCase();
        if (normalized.isBlank()) {
            return null;
        }
        if (!normalized.matches("^[A-Z0-9_]+$")) {
            throw new ResponseStatusException(BAD_REQUEST, "autoUnlockRule must match [A-Z0-9_]+");
        }
        return normalized;
    }

    private void validateRuleBinding(String ruleType, String legacyRule) {
        if (ruleType == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Each achievement must configure ruleType");
        }
        if (legacyRule != null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Legacy autoUnlockRule is no longer supported");
        }
    }

    private String normalizeRuleType(String rawRuleType) {
        if (rawRuleType == null) {
            return null;
        }

        String normalized = rawRuleType.trim().toUpperCase();
        if (normalized.isBlank()) {
            return null;
        }
        if (!normalized.matches("^[A-Z0-9_]+$")) {
            throw new ResponseStatusException(BAD_REQUEST, "ruleType must match [A-Z0-9_]+");
        }
        return normalized;
    }

    private String normalizeRuleConfigJson(String rawRuleConfigJson) {
        if (rawRuleConfigJson == null) {
            return null;
        }
        String normalized = rawRuleConfigJson.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void validateParameterizedRule(
            String ruleType,
            Integer threshold,
            Integer thresholdSecondary,
            String ruleConfigJson) {
        if (ruleType == null) {
            return;
        }

        switch (ruleType) {
            case RULE_TYPE_CUMULATIVE_EXAM_ATTEMPTS -> {
                if (threshold == null || threshold <= 0) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "ruleThreshold must be > 0 for CUMULATIVE_EXAM_ATTEMPTS");
                }
            }
            case RULE_TYPE_CUMULATIVE_STUDY_MINUTES -> {
                if (threshold == null || threshold <= 0) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "ruleThreshold must be > 0 for CUMULATIVE_STUDY_MINUTES");
                }
            }
            case RULE_TYPE_STREAK_DAYS -> {
                if (threshold == null || threshold <= 0) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "ruleThreshold must be > 0 for STREAK_DAYS");
                }
            }
            case RULE_TYPE_QUALITY_MIN_SCORE_ATTEMPTS -> {
                if (threshold == null || threshold < 0 || threshold > 100) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "ruleThreshold must be in range 0..100 for QUALITY_MIN_SCORE_ATTEMPTS");
                }
                if (thresholdSecondary == null || thresholdSecondary <= 0) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "ruleThresholdSecondary must be > 0 for QUALITY_MIN_SCORE_ATTEMPTS");
                }
            }
            case RULE_TYPE_COMPOUND -> {
                CompoundRuleConfig config = parseCompoundRuleConfig(ruleConfigJson);
                if (config == null || config.clauses() == null || config.clauses().size() < 2) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "COMPOUND_RULE requires at least 2 clauses in ruleConfigJson");
                }
                String logic = Optional.ofNullable(config.logic()).map(String::trim).map(String::toUpperCase).orElse("");
                if (!LOGIC_AND.equals(logic) && !LOGIC_OR.equals(logic)) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "COMPOUND_RULE logic must be AND or OR");
                }
                for (CompoundRuleClause clause : config.clauses()) {
                    String clauseRuleType = normalizeRuleType(clause.ruleType());
                    if (clauseRuleType == null || RULE_TYPE_COMPOUND.equals(clauseRuleType)) {
                        throw new ResponseStatusException(BAD_REQUEST,
                                "COMPOUND_RULE clauses must use non-compound ruleType");
                    }
                    validateParameterizedRule(clauseRuleType, clause.threshold(), clause.thresholdSecondary(), null);
                }
            }
            default -> throw new ResponseStatusException(BAD_REQUEST,
                    "Unsupported ruleType. Supported values: CUMULATIVE_EXAM_ATTEMPTS, CUMULATIVE_STUDY_MINUTES, STREAK_DAYS, QUALITY_MIN_SCORE_ATTEMPTS, COMPOUND_RULE");
        }
    }

            private record CompoundRuleConfig(String logic, List<CompoundRuleClause> clauses) {
            }

            private record CompoundRuleClause(String ruleType, Integer threshold, Integer thresholdSecondary) {
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

}
