package com.exam_bank.study_service.feature.scheduler.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.validation.ConstraintViolationException;

import com.exam_bank.study_service.config.SecurityConfig;
import com.exam_bank.study_service.feature.scheduler.dto.ManualReviewResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDeckDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDecksResponseDto;
import com.exam_bank.study_service.feature.scheduler.service.SpacedRepetitionService;
import com.exam_bank.study_service.service.AuthenticatedUserService;

@WebMvcTest(SpacedRepetitionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "auth.jwt.issuer=auth_service",
        "auth.jwt.secret=VjNyeVNlY3VyZVNlY3JldEtleUZvckF1dGhTZXJ2aWNlMTIzNDU2Nzg5MDE="
})
class SpacedRepetitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpacedRepetitionService spacedRepetitionService;

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
    void getDueCards_shouldReturn401_whenRequestIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/spaced-repetition/me/due"))
                .andExpect(status().isUnauthorized());

        verify(spacedRepetitionService, never()).getDueCards(any(), any());
    }

    @Test
    void getDueCards_shouldReturn401_whenTokenIsInvalid() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(get("/spaced-repetition/me/due")
                .header("Authorization", "Bearer bad-token")))
                .hasRootCauseInstanceOf(JwtException.class);

        verify(spacedRepetitionService, never()).getDueCards(any(), any());
    }

    @Test
    void getDueCards_shouldReturn400_whenLimitIsOutOfRange() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(15L);

        assertThatThrownBy(() -> mockMvc.perform(get("/spaced-repetition/me/due")
                .header("Authorization", "Bearer valid-token")
                .param("limit", "0")))
                .hasRootCauseInstanceOf(ConstraintViolationException.class);

        verify(spacedRepetitionService, never()).getDueCards(any(), any());
    }

    @Test
    void getExamDecks_shouldReturnDecks_whenAuthenticated() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(15L);
        when(spacedRepetitionService.getExamDecks(15L)).thenReturn(new Sm2ExamDecksResponseDto(
                Instant.parse("2026-04-07T15:00:00Z"),
                1,
                2,
                List.of(new Sm2ExamDeckDto(
                        10L,
                        "Exam 10",
                        500L,
                        Instant.parse("2026-04-07T14:50:00Z"),
                        2,
                        List.of()))));

        mockMvc.perform(get("/spaced-repetition/me/exam-decks")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deckCount").value(1))
                .andExpect(jsonPath("$.totalWrongQuestions").value(2))
                .andExpect(jsonPath("$.decks[0].examId").value(10))
                .andExpect(jsonPath("$.decks[0].examTitle").value("Exam 10"));

        verify(spacedRepetitionService).getExamDecks(15L);
    }

    @Test
    void submitManualReview_shouldReturn400_whenRequiredFieldsMissing() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(15L);

        mockMvc.perform(post("/spaced-repetition/me/review")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isCorrect\":true}"))
                .andExpect(status().isBadRequest());

        verify(spacedRepetitionService, never()).submitManualReview(any(), any(), any(), any(), any());
    }

    @Test
    void submitManualReview_shouldDelegateToService_whenPayloadIsValid() throws Exception {
        when(userService.getCurrentUserId()).thenReturn(15L);
        when(spacedRepetitionService.submitManualReview(15L, 99L, true, 1234L, 1)).thenReturn(
                new ManualReviewResponseDto(
                        700L,
                        99L,
                        5,
                        1,
                        1,
                        2.6d,
                        Instant.parse("2026-04-08T15:00:00Z")));

        mockMvc.perform(post("/spaced-repetition/me/review")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":99,\"isCorrect\":true,\"responseTimeMs\":1234,\"answerChangeCount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(700))
                .andExpect(jsonPath("$.itemId").value(99))
                .andExpect(jsonPath("$.quality").value(5))
                .andExpect(jsonPath("$.repetition").value(1));

        verify(spacedRepetitionService).submitManualReview(15L, 99L, true, 1234L, 1);
    }

    private Jwt validJwt() {
        return new Jwt(
                "valid-token",
                Instant.parse("2026-04-07T14:00:00Z"),
                Instant.parse("2026-04-07T16:00:00Z"),
                Map.of("alg", "HS256"),
                Map.of(
                        "iss", "auth_service",
                        "sub", "user-15",
                        "role", "USER",
                        "userId", 15L));
    }
}