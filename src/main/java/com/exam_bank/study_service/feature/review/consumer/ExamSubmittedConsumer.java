package com.exam_bank.study_service.feature.review.consumer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.exam_bank.study_service.feature.review.dto.ExamSubmittedEventDto;
import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;
import com.exam_bank.study_service.feature.review.entity.ReviewSource;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.feature.scheduler.service.SpacedRepetitionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamSubmittedConsumer {

    private final StudyReviewEventRepository studyReviewEventRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final GamificationService gamificationService;

    @RabbitListener(queues = "${study.events.exam-submitted.queue:study.exam-submitted.queue}")
    @Transactional
    public void onExamSubmitted(ExamSubmittedEventDto event) {
        log.info("Received ExamSubmittedEvent: attemptId={}, userId={}, examId={}",
                event.getAttemptId(), event.getUserId(), event.getExamId());

        List<StudyReviewEvent> events = new ArrayList<>();

        long fallbackLatencyPerQuestionMs = resolveFallbackLatencyPerQuestionMs(event);
        for (ExamSubmittedEventDto.QuestionAnsweredDto q : event.getQuestions()) {
            StudyReviewEvent reviewEvent = new StudyReviewEvent();
            reviewEvent.setUserId(event.getUserId());
            reviewEvent.setItemId(q.getQuestionId());
            reviewEvent.setAttemptId(event.getAttemptId());
            reviewEvent.setExamId(event.getExamId());
            reviewEvent.setExamTitle(event.getExamTitle());
            reviewEvent.setEvaluatedAt(event.getSubmittedAt() != null ? event.getSubmittedAt() : Instant.now());
            reviewEvent.setQuality(mapQuality(q));
            reviewEvent.setIsCorrect(Boolean.TRUE.equals(q.getIsCorrect()));
            reviewEvent.setScoreEarned(q.getEarnedScore() != null ? q.getEarnedScore() : 0.0);
            reviewEvent.setScoreMax(q.getMaxScore() != null ? q.getMaxScore() : 1.0);
            reviewEvent.setScorePercent(computeScorePercent(q));
            reviewEvent.setSelectedOptionIds(q.getSelectedOptionIds());
            reviewEvent.setCorrectOptionIds(q.getCorrectOptionIds());
            reviewEvent.setLatencyMs(resolveLatencyMs(q.getResponseTimeMs(), fallbackLatencyPerQuestionMs));
            reviewEvent.setAnswerChangeCount(q.getAnswerChangeCount() != null ? q.getAnswerChangeCount() : 0);
            reviewEvent.setDifficulty(q.getDifficulty());
            reviewEvent.setTopicTagIds(q.getTagIds());
            reviewEvent.setSource(ReviewSource.EXAM_SUBMISSION);

            events.add(reviewEvent);
        }

        studyReviewEventRepository.saveAll(events);
        spacedRepetitionService.applyExamEvents(events);
        gamificationService.refreshProgressForReview(event.getUserId(), event.getSubmittedAt());
        log.info("Saved {} StudyReviewEvents for attemptId={}", events.size(), event.getAttemptId());
    }

    private long resolveFallbackLatencyPerQuestionMs(ExamSubmittedEventDto event) {
        if (event.getDurationSeconds() == null || event.getDurationSeconds() <= 0) {
            return 0L;
        }
        int questionCount = event.getQuestions() != null ? event.getQuestions().size() : 0;
        if (questionCount <= 0) {
            return 0L;
        }
        return (event.getDurationSeconds() * 1000L) / questionCount;
    }

    private long resolveLatencyMs(Long responseTimeMs, long fallbackLatencyPerQuestionMs) {
        if (responseTimeMs != null && responseTimeMs > 0) {
            return responseTimeMs;
        }
        return Math.max(fallbackLatencyPerQuestionMs, 0L);
    }

    private int mapQuality(ExamSubmittedEventDto.QuestionAnsweredDto q) {
        if (q.getIsCorrect() == null || !q.getIsCorrect()) {
            return 0;
        }

        Long latency = q.getResponseTimeMs();
        int changes = q.getAnswerChangeCount() != null ? q.getAnswerChangeCount() : 0;

        if (latency != null && latency <= 15_000L && changes == 0) {
            return 5;
        }
        if (latency != null && latency <= 30_000L) {
            return 4;
        }
        return 3;
    }

    private double computeScorePercent(ExamSubmittedEventDto.QuestionAnsweredDto q) {
        if (q.getMaxScore() == null || q.getMaxScore() <= 0) {
            return 0.0;
        }
        double earned = q.getEarnedScore() != null ? q.getEarnedScore() : 0.0;
        return Math.round(earned / q.getMaxScore() * 100.0 * 100.0) / 100.0;
    }
}
