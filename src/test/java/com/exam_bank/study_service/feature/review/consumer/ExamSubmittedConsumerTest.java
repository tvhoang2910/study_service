package com.exam_bank.study_service.feature.review.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.UnexpectedRollbackException;

import com.exam_bank.study_service.feature.review.dto.ExamSubmittedEventDto;
import com.exam_bank.study_service.feature.review.entity.ReviewSource;
import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.feature.scheduler.service.SpacedRepetitionService;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ExamSubmittedConsumerTest {

    @Mock
    private StudyReviewEventRepository studyReviewEventRepository;

    @Mock
    private SpacedRepetitionService spacedRepetitionService;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private ExamSubmittedConsumer consumer;

    @Test
    void onExamSubmitted_shouldMapQuestionsPersistAndForwardToSm2Service() {
        when(studyReviewEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmittedEventDto event = new ExamSubmittedEventDto();
        event.setAttemptId(999L);
        event.setUserId(12L);
        event.setExamId(88L);
        event.setExamTitle("SM2 Final");
        event.setSubmittedAt(Instant.parse("2026-04-07T09:00:00Z"));
        event.setQuestions(List.of(
                buildQuestion(101L, true, 1.0d, 1.0d, "201", "201", 12_000L, 0, 0.2d, "3,4"),
                buildQuestion(102L, false, 0.0d, 1.0d, "202", "203", 45_000L, 2, 0.8d, "9")));

        consumer.onExamSubmitted(event);

        ArgumentCaptor<List<StudyReviewEvent>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(studyReviewEventRepository).saveAll(saveCaptor.capture());
        List<StudyReviewEvent> savedEvents = saveCaptor.getValue();

        assertThat(savedEvents).hasSize(2);

        StudyReviewEvent first = savedEvents.getFirst();
        assertThat(first.getUserId()).isEqualTo(12L);
        assertThat(first.getAttemptId()).isEqualTo(999L);
        assertThat(first.getExamId()).isEqualTo(88L);
        assertThat(first.getExamTitle()).isEqualTo("SM2 Final");
        assertThat(first.getItemId()).isEqualTo(101L);
        assertThat(first.getQuality()).isEqualTo(5);
        assertThat(first.getIsCorrect()).isTrue();
        assertThat(first.getScorePercent()).isEqualTo(100.0d);
        assertThat(first.getSource()).isEqualTo(ReviewSource.EXAM_SUBMISSION);
        assertThat(first.getTopicTagIds()).isEqualTo("3,4");

        StudyReviewEvent second = savedEvents.get(1);
        assertThat(second.getItemId()).isEqualTo(102L);
        assertThat(second.getQuality()).isEqualTo(0);
        assertThat(second.getIsCorrect()).isFalse();
        assertThat(second.getScorePercent()).isEqualTo(0.0d);
        assertThat(second.getSource()).isEqualTo(ReviewSource.EXAM_SUBMISSION);

        ArgumentCaptor<List<StudyReviewEvent>> sm2Captor = ArgumentCaptor.forClass(List.class);
        verify(spacedRepetitionService).applyExamEvents(sm2Captor.capture());
        assertThat(sm2Captor.getValue()).hasSize(2);
        assertThat(sm2Captor.getValue().getFirst().getItemId()).isEqualTo(101L);
        verify(gamificationService).unlockAchievementsForReview(12L, Instant.parse("2026-04-07T09:00:00Z"));
    }

    @Test
    void onExamSubmitted_shouldUseDefaultFallbacks_forMissingOptionalQuestionFields() {
        when(studyReviewEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmittedEventDto event = new ExamSubmittedEventDto();
        event.setAttemptId(111L);
        event.setUserId(42L);
        event.setExamId(7L);
        event.setSubmittedAt(null);
        event.setQuestions(List.of(buildQuestion(501L, true, null, null, null, null, null, null, null, null)));

        consumer.onExamSubmitted(event);

        ArgumentCaptor<List<StudyReviewEvent>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(studyReviewEventRepository).saveAll(saveCaptor.capture());
        StudyReviewEvent saved = saveCaptor.getValue().getFirst();

        assertThat(saved.getEvaluatedAt()).isNotNull();
        assertThat(saved.getQuality()).isEqualTo(3);
        assertThat(saved.getScoreEarned()).isEqualTo(0.0d);
        assertThat(saved.getScoreMax()).isEqualTo(1.0d);
        assertThat(saved.getScorePercent()).isEqualTo(0.0d);
        assertThat(saved.getAnswerChangeCount()).isEqualTo(0);
        verify(gamificationService).unlockAchievementsForReview(42L, null);
    }

    @Test
    void onExamSubmitted_shouldMapQualityFour_forMediumLatencyEvenWithAnswerChanges() {
        when(studyReviewEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmittedEventDto event = new ExamSubmittedEventDto();
        event.setAttemptId(222L);
        event.setUserId(10L);
        event.setExamId(55L);
        event.setSubmittedAt(Instant.parse("2026-04-07T10:00:00Z"));
        event.setQuestions(List.of(buildQuestion(601L, true, 1.0d, 1.0d, "1", "1", 25_000L, 9, 0.5d, "12")));

        consumer.onExamSubmitted(event);

        ArgumentCaptor<List<StudyReviewEvent>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(studyReviewEventRepository).saveAll(saveCaptor.capture());
        StudyReviewEvent saved = saveCaptor.getValue().getFirst();

        assertThat(saved.getQuality()).isEqualTo(4);
        verify(gamificationService).unlockAchievementsForReview(10L, Instant.parse("2026-04-07T10:00:00Z"));
    }

    @Test
    void onExamSubmitted_shouldNotFail_whenAchievementUnlockIsNonCriticalFailure() {
        when(studyReviewEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant submittedAt = Instant.parse("2026-04-07T10:30:00Z");
        doThrow(new UnexpectedRollbackException("rollback-only"))
                .when(gamificationService)
                .unlockAchievementsForReview(77L, submittedAt);

        ExamSubmittedEventDto event = new ExamSubmittedEventDto();
        event.setAttemptId(333L);
        event.setUserId(77L);
        event.setExamId(99L);
        event.setSubmittedAt(submittedAt);
        event.setQuestions(List.of(buildQuestion(701L, false, 0.0d, 1.0d, "10", "11", 12_000L, 0, 0.4d, "2")));

        assertThatCode(() -> consumer.onExamSubmitted(event)).doesNotThrowAnyException();

        verify(gamificationService).unlockAchievementsForReview(77L, submittedAt);
    }

    private ExamSubmittedEventDto.QuestionAnsweredDto buildQuestion(
            Long questionId,
            Boolean isCorrect,
            Double earnedScore,
            Double maxScore,
            String selectedOptionIds,
            String correctOptionIds,
            Long responseTimeMs,
            Integer answerChangeCount,
            Double difficulty,
            String tagIds) {
        ExamSubmittedEventDto.QuestionAnsweredDto question = new ExamSubmittedEventDto.QuestionAnsweredDto();
        question.setQuestionId(questionId);
        question.setIsCorrect(isCorrect);
        question.setEarnedScore(earnedScore);
        question.setMaxScore(maxScore);
        question.setSelectedOptionIds(selectedOptionIds);
        question.setCorrectOptionIds(correctOptionIds);
        question.setResponseTimeMs(responseTimeMs);
        question.setAnswerChangeCount(answerChangeCount);
        question.setDifficulty(difficulty);
        question.setTagIds(tagIds);
        return question;
    }
}