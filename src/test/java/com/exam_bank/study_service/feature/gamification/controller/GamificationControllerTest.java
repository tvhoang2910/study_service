package com.exam_bank.study_service.feature.gamification.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.exam_bank.study_service.config.SecurityConfig;
import com.exam_bank.study_service.feature.gamification.dto.AchievementViewDto;
import com.exam_bank.study_service.feature.gamification.dto.CalendarDayDto;
import com.exam_bank.study_service.feature.gamification.dto.GamificationOverviewDto;
import com.exam_bank.study_service.feature.gamification.dto.LeaderboardEntryDto;
import com.exam_bank.study_service.feature.gamification.dto.StreakCalendarDto;
import com.exam_bank.study_service.feature.gamification.entity.AchievementCode;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.service.AuthenticatedUserService;

@WebMvcTest(GamificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.jwt.issuer=auth_service",
        "auth.jwt.secret=VjNyeVNlY3VyZVNlY3JldEtleUZvckF1dGhTZXJ2aWNlMTIzNDU2Nzg5MDE="
})
class GamificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GamificationService gamificationService;

    @MockitoBean
    private AuthenticatedUserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setup() {
        when(jwtDecoder.decode("valid-token")).thenReturn(validJwt());
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("Invalid token"));
    }

    @Test
    void getOverview_shouldReturn401_whenRequestUnauthenticated() throws Exception {
        mockMvc.perform(get("/gamification/me/overview"))
                .andExpect(status().isUnauthorized());

        verify(gamificationService, never()).getOverview(any());
    }

    @Test
    void getOverview_shouldReturn401_whenTokenInvalid() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(get("/gamification/me/overview")
                .header("Authorization", "Bearer bad-token")))
                .hasRootCauseInstanceOf(JwtException.class);

        verify(gamificationService, never()).getOverview(any());
    }

    @Test
    void getOverview_shouldReturnOverviewPayload_whenAuthenticated() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(88L);
        when(gamificationService.getOverview(88L)).thenReturn(GamificationOverviewDto.builder()
                .streakDays(4)
                .longestStreak(7)
                .dailyStudyMinutes(16)
                .dailyTargetMinutes(15)
                .todayQualified(true)
                .justQualifiedToday(true)
                .points(760)
                .newlyUnlockedAchievements(List.of())
                .recentUnlockedAchievements(List.of())
                .build());

        mockMvc.perform(get("/gamification/me/overview")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streakDays").value(4))
                .andExpect(jsonPath("$.points").value(760));

        verify(gamificationService).getOverview(88L);
    }

    @Test
    void getAchievements_shouldReturnAchievementList_whenAuthenticated() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(88L);
        when(gamificationService.getAchievements(88L)).thenReturn(List.of(
                AchievementViewDto.builder()
                        .code(AchievementCode.SCHOLAR)
                        .name("Học bá")
                        .description("Học đủ 5 phút")
                        .icon("BOOK_OPEN")
                        .groupName("Học thuật")
                        .points(300)
                        .unlocked(true)
                        .unlockedAt(Instant.parse("2026-04-10T10:00:00Z"))
                        .build()));

        mockMvc.perform(get("/gamification/me/achievements")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SCHOLAR"))
                .andExpect(jsonPath("$[0].points").value(300));

        verify(gamificationService).getAchievements(88L);
    }

    @Test
    void getLeaderboard_shouldForwardLimitParamToService() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(88L);
        when(gamificationService.getLeaderboard(88L, 12)).thenReturn(List.of(
                LeaderboardEntryDto.builder()
                        .rank(1)
                        .userId(88L)
                        .displayName("Bạn")
                        .points(980)
                        .streakDays(8)
                        .unlockedAchievements(4)
                        .currentUser(true)
                        .build()));

        mockMvc.perform(get("/gamification/me/leaderboard")
                .header("Authorization", "Bearer valid-token")
                .param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].currentUser").value(true));

        verify(gamificationService).getLeaderboard(88L, 12);
    }

    @Test
    void getCalendar_shouldUseCurrentMonth_whenMonthAbsent() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(88L);
        when(gamificationService.getStreakCalendar(eq(88L), any(YearMonth.class))).thenAnswer(invocation -> {
            YearMonth month = invocation.getArgument(1);
            return StreakCalendarDto.builder()
                    .month(month.toString())
                    .totalDays(month.lengthOfMonth())
                    .activityDays(2)
                    .qualifiedDays(1)
                    .days(List.of(
                            CalendarDayDto.builder()
                                    .date(month.atDay(1))
                                    .activityCompleted(true)
                                    .streakQualified(false)
                                    .build()))
                    .build();
        });

        mockMvc.perform(get("/gamification/me/calendar")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").exists())
                .andExpect(jsonPath("$.activityDays").value(2));

        verify(gamificationService).getStreakCalendar(eq(88L), any(YearMonth.class));
    }

    @Test
    void getCalendar_shouldReturn400_whenMonthFormatInvalid() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(88L);

        mockMvc.perform(get("/gamification/me/calendar")
                .header("Authorization", "Bearer valid-token")
                .param("month", "04-2026"))
                .andExpect(status().isBadRequest());

        verify(gamificationService, never()).getStreakCalendar(any(), any());
    }

    @Test
    void markShared_shouldDelegateToService_whenAuthenticated() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(88L);

        mockMvc.perform(post("/gamification/me/share")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(gamificationService).markLearningAmbassadorShared(88L);
    }

    private Jwt validJwt() {
        return new Jwt(
                "valid-token",
                Instant.parse("2026-04-07T14:00:00Z"),
                Instant.parse("2026-04-07T16:00:00Z"),
                Map.of("alg", "HS256"),
                Map.of(
                        "iss", "auth_service",
                        "sub", "user-88",
                        "role", "USER",
                        "userId", 88L));
    }
}
