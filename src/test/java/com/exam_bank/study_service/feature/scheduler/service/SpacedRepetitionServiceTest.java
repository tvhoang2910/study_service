package com.exam_bank.study_service.feature.scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.feature.review.entity.ReviewSource;
import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository.LatestWrongQuestionProjection;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.feature.scheduler.dto.DueCardsResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.ManualReviewResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2DeckQuestionDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDeckDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDecksResponseDto;
import com.exam_bank.study_service.feature.scheduler.entity.StudyCard;
import com.exam_bank.study_service.feature.scheduler.entity.StudyCardReviewHistory;
import com.exam_bank.study_service.feature.scheduler.repository.StudyCardRepository;
import com.exam_bank.study_service.feature.scheduler.repository.StudyCardReviewHistoryRepository;

@ExtendWith(MockitoExtension.class)
class SpacedRepetitionServiceTest {

    @Mock
    private StudyCardRepository studyCardRepository;

    @Mock
    private StudyCardReviewHistoryRepository historyRepository;

    @Mock
    private StudyReviewEventRepository reviewEventRepository;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private SpacedRepetitionService service;

    @Test
    void submitManualReview_shouldCreateCardAndScheduleNextDay_forFastCorrectAnswer() {
        stubPersistencePipeline();
        when(studyCardRepository.findByUserIdAndItemId(7L, 101L)).thenReturn(Optional.empty());

        Instant before = Instant.now();
        ManualReviewResponseDto response = service.submitManualReview(7L, 101L, true, 10_000L, 0);
        Instant after = Instant.now();

        assertThat(response.itemId()).isEqualTo(101L);
        assertThat(response.quality()).isEqualTo(5);
        assertThat(response.repetition()).isEqualTo(1);
        assertThat(response.intervalDays()).isEqualTo(1);
        assertThat(response.easinessFactor()).isEqualTo(2.6d);
        assertThat(response.nextReviewAt())
                .isAfterOrEqualTo(before.plus(1, ChronoUnit.DAYS))
                .isBeforeOrEqualTo(after.plus(1, ChronoUnit.DAYS).plusSeconds(1));

        ArgumentCaptor<StudyCard> cardCaptor = ArgumentCaptor.forClass(StudyCard.class);
        verify(studyCardRepository).save(cardCaptor.capture());

        StudyCard savedCard = cardCaptor.getValue();
        assertThat(savedCard.getUserId()).isEqualTo(7L);
        assertThat(savedCard.getItemId()).isEqualTo(101L);
        assertThat(savedCard.getLastIsCorrect()).isTrue();
        assertThat(savedCard.getLastQuality()).isEqualTo(5);
        assertThat(savedCard.getTotalReviews()).isEqualTo(1);
        assertThat(savedCard.getCorrectReviews()).isEqualTo(1);

        ArgumentCaptor<StudyCardReviewHistory> historyCaptor = ArgumentCaptor.forClass(StudyCardReviewHistory.class);
        verify(historyRepository).save(historyCaptor.capture());

        StudyCardReviewHistory history = historyCaptor.getValue();
        assertThat(history.getQuality()).isEqualTo(5);
        assertThat(history.getPrevRepetition()).isEqualTo(0);
        assertThat(history.getNextRepetition()).isEqualTo(1);
        assertThat(history.getPrevIntervalDays()).isEqualTo(0);
        assertThat(history.getNextIntervalDays()).isEqualTo(1);

        verify(gamificationService).refreshProgressForReview(eq(7L), any(Instant.class));
    }

    @Test
    void submitManualReview_shouldResetRepetitionAndScheduleImmediate_forIncorrectAnswer() {
        stubPersistencePipeline();

        StudyCard existing = new StudyCard();
        existing.setId(99L);
        existing.setUserId(3L);
        existing.setItemId(300L);
        existing.setRepetition(4);
        existing.setIntervalDays(18);
        existing.setEasinessFactor(2.2d);
        existing.setTotalReviews(8);
        existing.setCorrectReviews(6);

        when(studyCardRepository.findByUserIdAndItemId(3L, 300L)).thenReturn(Optional.of(existing));

        Instant before = Instant.now();
        ManualReviewResponseDto response = service.submitManualReview(3L, 300L, false, 45_000L, 2);
        Instant after = Instant.now();

        assertThat(response.quality()).isEqualTo(0);
        assertThat(response.repetition()).isEqualTo(0);
        assertThat(response.intervalDays()).isEqualTo(0);
        assertThat(response.nextReviewAt())
            .isAfterOrEqualTo(before)
            .isBeforeOrEqualTo(after.plusSeconds(1));

        ArgumentCaptor<StudyCard> cardCaptor = ArgumentCaptor.forClass(StudyCard.class);
        verify(studyCardRepository).save(cardCaptor.capture());

        StudyCard updatedCard = cardCaptor.getValue();
        assertThat(updatedCard.getCorrectReviews()).isEqualTo(6);
        assertThat(updatedCard.getTotalReviews()).isEqualTo(9);
        assertThat(updatedCard.getLastIsCorrect()).isFalse();
        assertThat(updatedCard.getLastQuality()).isEqualTo(0);

        verify(gamificationService).refreshProgressForReview(eq(3L), any(Instant.class));
    }

    @Test
    void submitManualReview_shouldAssignQualityFour_forMediumLatencyAndOneChange() {
        stubPersistencePipeline();
        when(studyCardRepository.findByUserIdAndItemId(5L, 500L)).thenReturn(Optional.empty());

        ManualReviewResponseDto response = service.submitManualReview(5L, 500L, true, 20_000L, 1);

        assertThat(response.quality()).isEqualTo(4);
        assertThat(response.repetition()).isEqualTo(1);
        assertThat(response.intervalDays()).isEqualTo(1);
    }

    @Test
    void submitManualReview_shouldRejectWhenCardIsNotDueYet() {
        StudyCard existing = new StudyCard();
        existing.setId(77L);
        existing.setUserId(6L);
        existing.setItemId(600L);
        existing.setNextReviewAt(Instant.now().plus(2, ChronoUnit.HOURS));

        when(studyCardRepository.findByUserIdAndItemId(6L, 600L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submitManualReview(6L, 600L, true, 5_000L, 0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode().value()).isEqualTo(409);
                    assertThat(exception.getReason()).contains("Review is not due yet");
                });

        verify(reviewEventRepository, never()).save(any(StudyReviewEvent.class));
        verify(studyCardRepository, never()).save(any(StudyCard.class));
        verifyNoInteractions(historyRepository);
        verifyNoInteractions(gamificationService);
    }

    @Test
    void applyExamEvents_shouldSkipNullAndCorrectEvents() {
        when(studyCardRepository.findByUserIdAndItemId(1L, 77L)).thenReturn(Optional.empty());
        when(studyCardRepository.save(any(StudyCard.class))).thenAnswer(invocation -> {
            StudyCard card = invocation.getArgument(0);
            if (card.getId() == null) {
                card.setId(701L);
            }
            return card;
        });
        when(historyRepository.save(any(StudyCardReviewHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudyReviewEvent correct = buildReviewEvent(1L, 66L, true, 5, 801L, Instant.parse("2026-04-07T09:00:00Z"));
        StudyReviewEvent wrong = buildReviewEvent(1L, 77L, false, 0, 802L, Instant.parse("2026-04-07T09:05:00Z"));

        service.applyExamEvents(Arrays.asList(correct, null, wrong));

        verify(studyCardRepository, times(1)).findByUserIdAndItemId(1L, 77L);
        verify(studyCardRepository, times(1)).save(any(StudyCard.class));
        verify(historyRepository, times(1)).save(any(StudyCardReviewHistory.class));
        verifyNoInteractions(reviewEventRepository);
        verifyNoInteractions(gamificationService);
    }

    @Test
    void getExamDecks_shouldFilterMasteredCards_andComputeDueFlag() {
        Instant now = Instant.now();
        List<LatestWrongQuestionProjection> rows = List.of(
                new ProjectionRow(1L, "Exam One", 99L, now.minus(1, ChronoUnit.DAYS), 11L, "1,2", "101", "102"),
                new ProjectionRow(1L, "Exam One", 99L, now.minus(1, ChronoUnit.DAYS), 12L, "1,3", "201", "202"),
                new ProjectionRow(2L, "Exam Two", 45L, now.minus(2, ChronoUnit.DAYS), 21L, "5", "301", "302"));

        when(reviewEventRepository.findLatestWrongQuestionsByExam(8L)).thenReturn(rows);

        StudyCard mastered = new StudyCard();
        mastered.setUserId(8L);
        mastered.setItemId(11L);
        mastered.setRepetition(5);
        mastered.setIntervalDays(8);
        mastered.setEasinessFactor(2.7d);
        mastered.setNextReviewAt(now.minus(1, ChronoUnit.DAYS));

        StudyCard notDueYet = new StudyCard();
        notDueYet.setUserId(8L);
        notDueYet.setItemId(12L);
        notDueYet.setRepetition(2);
        notDueYet.setIntervalDays(6);
        notDueYet.setEasinessFactor(2.3d);
        notDueYet.setNextReviewAt(now.plus(2, ChronoUnit.DAYS));
        notDueYet.setTotalReviews(4);
        notDueYet.setCorrectReviews(3);

        when(studyCardRepository.findByUserIdAndItemIdIn(eq(8L), anyList())).thenReturn(List.of(mastered, notDueYet));

        Sm2ExamDecksResponseDto response = service.getExamDecks(8L);

        assertThat(response.deckCount()).isEqualTo(2);
        assertThat(response.totalWrongQuestions()).isEqualTo(2);

        Sm2ExamDeckDto examOne = response.decks().stream()
                .filter(deck -> deck.examId().equals(1L))
                .findFirst()
                .orElseThrow();
        assertThat(examOne.wrongQuestionCount()).isEqualTo(1);
        assertThat(examOne.questions()).hasSize(1);
        Sm2DeckQuestionDto examOneQuestion = examOne.questions().getFirst();
        assertThat(examOneQuestion.itemId()).isEqualTo(12L);
        assertThat(examOneQuestion.dueNow()).isFalse();

        Sm2ExamDeckDto examTwo = response.decks().stream()
                .filter(deck -> deck.examId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertThat(examTwo.wrongQuestionCount()).isEqualTo(1);
        Sm2DeckQuestionDto examTwoQuestion = examTwo.questions().getFirst();
        assertThat(examTwoQuestion.itemId()).isEqualTo(21L);
        assertThat(examTwoQuestion.repetition()).isEqualTo(0);
        assertThat(examTwoQuestion.dueNow()).isTrue();
    }

    @Test
    void getDueCards_shouldApplyDefaultAndMaxLimitBounds() {
        StudyCard dueCard = new StudyCard();
        dueCard.setId(10L);
        dueCard.setUserId(5L);
        dueCard.setItemId(555L);
        dueCard.setRepetition(1);
        dueCard.setIntervalDays(1);
        dueCard.setEasinessFactor(2.5d);
        dueCard.setNextReviewAt(Instant.now());
        dueCard.setTotalReviews(2);
        dueCard.setCorrectReviews(1);

        when(studyCardRepository
                .findByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        eq(5L), eq(5), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(dueCard));
        when(studyCardRepository.countByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqual(eq(5L), eq(5),
                any(Instant.class)))
                .thenReturn(42L);

        DueCardsResponseDto defaultResponse = service.getDueCards(5L, null);
        DueCardsResponseDto cappedResponse = service.getDueCards(5L, 999);

        assertThat(defaultResponse.limit()).isEqualTo(20);
        assertThat(defaultResponse.dueCount()).isEqualTo(42L);
        assertThat(defaultResponse.cards()).hasSize(1);

        assertThat(cappedResponse.limit()).isEqualTo(100);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(studyCardRepository, times(2))
                .findByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        anyLong(), any(), any(Instant.class), pageableCaptor.capture());

        List<Pageable> pages = pageableCaptor.getAllValues();
        assertThat(pages.get(0).getPageSize()).isEqualTo(20);
        assertThat(pages.get(1).getPageSize()).isEqualTo(100);
    }

    @Test
    void getCardForManualReview_shouldThrowNotFound_whenCardMissing() {
        when(studyCardRepository.findByUserIdAndItemId(9L, 90L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCardForManualReview(9L, 90L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode().value()).isEqualTo(404);
                    assertThat(exception.getReason()).contains("No study card found");
                });
    }

    private void stubPersistencePipeline() {
        when(reviewEventRepository.save(any(StudyReviewEvent.class))).thenAnswer(invocation -> {
            StudyReviewEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId(5000L);
            }
            return event;
        });

        when(studyCardRepository.save(any(StudyCard.class))).thenAnswer(invocation -> {
            StudyCard card = invocation.getArgument(0);
            if (card.getId() == null) {
                card.setId(1000L);
            }
            return card;
        });

        when(historyRepository.save(any(StudyCardReviewHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StudyReviewEvent buildReviewEvent(Long userId, Long itemId, boolean isCorrect, int quality, Long id,
            Instant evaluatedAt) {
        StudyReviewEvent event = new StudyReviewEvent();
        event.setId(id);
        event.setUserId(userId);
        event.setItemId(itemId);
        event.setAttemptId(12345L);
        event.setExamId(999L);
        event.setEvaluatedAt(evaluatedAt);
        event.setQuality(quality);
        event.setIsCorrect(isCorrect);
        event.setSource(ReviewSource.EXAM_SUBMISSION);
        return event;
    }

    private static final class ProjectionRow implements LatestWrongQuestionProjection {

        private final Long examId;
        private final String examTitle;
        private final Long attemptId;
        private final Instant submittedAt;
        private final Long itemId;
        private final String topicTagIds;
        private final String selectedOptionIds;
        private final String correctOptionIds;

        private ProjectionRow(
                Long examId,
                String examTitle,
                Long attemptId,
                Instant submittedAt,
                Long itemId,
                String topicTagIds,
                String selectedOptionIds,
                String correctOptionIds) {
            this.examId = examId;
            this.examTitle = examTitle;
            this.attemptId = attemptId;
            this.submittedAt = submittedAt;
            this.itemId = itemId;
            this.topicTagIds = topicTagIds;
            this.selectedOptionIds = selectedOptionIds;
            this.correctOptionIds = correctOptionIds;
        }

        @Override
        public Long getExamId() {
            return examId;
        }

        @Override
        public String getExamTitle() {
            return examTitle;
        }

        @Override
        public Long getAttemptId() {
            return attemptId;
        }

        @Override
        public Instant getSubmittedAt() {
            return submittedAt;
        }

        @Override
        public Long getItemId() {
            return itemId;
        }

        @Override
        public String getTopicTagIds() {
            return topicTagIds;
        }

        @Override
        public String getSelectedOptionIds() {
            return selectedOptionIds;
        }

        @Override
        public String getCorrectOptionIds() {
            return correctOptionIds;
        }
    }
}