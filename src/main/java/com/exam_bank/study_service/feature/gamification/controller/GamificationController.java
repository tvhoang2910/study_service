package com.exam_bank.study_service.feature.gamification.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.dto.StreakCalendarDto;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.service.AuthenticatedUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

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
}
