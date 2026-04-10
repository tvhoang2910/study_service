package com.exam_bank.study_service.feature.gamification.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.feature.gamification.dto.AdminAchievementUpsertRequestDto;
import com.exam_bank.study_service.feature.gamification.dto.AdminAssignAchievementRequestDto;
import com.exam_bank.study_service.feature.gamification.dto.AdminAchievementUpdateRequestDto;
import com.exam_bank.study_service.feature.gamification.dto.AchievementDefinitionDto;
import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.dto.StreakCalendarDto;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.security.Role;
import com.exam_bank.study_service.service.AuthenticatedUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/gamification")
@RequiredArgsConstructor
@Slf4j
public class GamificationController {

    private final GamificationService gamificationService;
    private final AuthenticatedUserService userService;

    @GetMapping("/me/overview")
    public ResponseEntity<GamificationOverviewDto> getOverview() {
        Long userId = userService.getCurrentUserId();
        GamificationOverviewDto response = gamificationService.getOverview(userId);
        log.debug("getOverview: userId={}, streakDays={}, points={}", userId, response.streakDays(), response.points());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/achievements")
    public ResponseEntity<List<AchievementViewDto>> getAchievements() {
        Long userId = userService.getCurrentUserId();
        List<AchievementViewDto> response = gamificationService.getAchievements(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard(
            @RequestParam(name = "limit", required = false) Integer limit) {
        Long userId = userService.getCurrentUserId();
        List<LeaderboardEntryDto> response = gamificationService.getLeaderboard(userId, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/calendar")
    public ResponseEntity<StreakCalendarDto> getCalendar(@RequestParam(name = "month", required = false) String month) {
        Long userId = userService.getCurrentUserId();
        YearMonth targetMonth;
        try {
            targetMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid month format. Use yyyy-MM");
        }

        return ResponseEntity.ok(gamificationService.getStreakCalendar(userId, targetMonth));
    }

    @PostMapping("/me/share")
    public ResponseEntity<Void> markShared() {
        Long userId = userService.getCurrentUserId();
        gamificationService.markLearningAmbassadorShared(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/achievements")
    public ResponseEntity<List<AchievementDefinitionDto>> getAchievementDefinitionsForAdmin() {
        ensureAdmin();
        return ResponseEntity.ok(gamificationService.getAchievementDefinitionsForAdmin());
    }

    @PostMapping("/admin/achievements")
    public ResponseEntity<AchievementDefinitionDto> createAchievement(@Valid @RequestBody AdminAchievementUpsertRequestDto request) {
        ensureAdmin();
        return ResponseEntity.ok(gamificationService.upsertAchievementDefinition(request));
    }

    @PutMapping("/admin/achievements/{code}")
    public ResponseEntity<AchievementDefinitionDto> updateAchievement(
            @PathVariable String code,
            @Valid @RequestBody AdminAchievementUpdateRequestDto request) {
        ensureAdmin();
        AdminAchievementUpsertRequestDto mergedRequest = new AdminAchievementUpsertRequestDto(
                code,
                request.name(),
                request.description(),
                request.icon(),
                request.groupName(),
                request.points(),
                request.active(),
            request.autoUnlockRule(),
            request.ruleType(),
            request.ruleThreshold(),
                request.ruleThresholdSecondary(),
                request.ruleConfigJson());
        return ResponseEntity.ok(gamificationService.upsertAchievementDefinition(mergedRequest));
    }

    @DeleteMapping("/admin/achievements/{code}")
    public ResponseEntity<Void> deactivateAchievement(@PathVariable String code) {
        ensureAdmin();
        gamificationService.deleteAchievementDefinition(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/achievements/{code}/assign")
    public ResponseEntity<Void> assignAchievement(
            @PathVariable String code,
            @Valid @RequestBody AdminAssignAchievementRequestDto request) {
        ensureAdmin();
        gamificationService.assignAchievementToUser(code, request.userId());
        return ResponseEntity.ok().build();
    }

    private void ensureAdmin() {
        if (userService.getCurrentUserRole() != Role.ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Admin role required");
        }
    }
}
