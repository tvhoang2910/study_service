package com.exam_bank.study_service.feature.review.consumer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.exam_bank.study_service.config.RabbitConfig;
import com.exam_bank.study_service.feature.review.dto.ExamSubmittedEventDto;
import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;
import com.exam_bank.study_service.feature.review.entity.ReviewSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamSubmittedConsumer {

    private final StudyReviewEventRepository studyReviewEventRepository;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
    public void onExamSubmitted(ExamSubmittedEventDto event) {
        log.info("Received ExamSubmittedEvent: attemptId={}, userId={}, examId={}",
                event.getAttemptId(), event.getUserId(), event.getExamId());

        List<StudyReviewEvent> events = new ArrayList<>();

        for (ExamSubmittedEventDto.QuestionAnsweredDto q : event.getQuestions()) {
            StudyReviewEvent reviewEvent = new StudyReviewEvent();
            reviewEvent.setUserId(event.getUserId());
            reviewEvent.setItemId(q.getQuestionId());
            reviewEvent.setAttemptId(event.getAttemptId());
            reviewEvent.setExamId(event.getExamId());
            reviewEvent.setEvaluatedAt(event.getSubmittedAt() != null ? event.getSubmittedAt() : Instant.now());
            reviewEvent.setQuality(mapQuality(q));
            reviewEvent.setIsCorrect(Boolean.TRUE.equals(q.getIsCorrect()));
            reviewEvent.setScoreEarned(q.getEarnedScore() != null ? q.getEarnedScore() : 0.0);
            reviewEvent.setScoreMax(q.getMaxScore() != null ? q.getMaxScore() : 1.0);
            reviewEvent.setScorePercent(computeScorePercent(q));
            reviewEvent.setLatencyMs(q.getResponseTimeMs());
            reviewEvent.setAnswerChangeCount(q.getAnswerChangeCount() != null ? q.getAnswerChangeCount() : 0);
            reviewEvent.setDifficulty(q.getDifficulty());
            reviewEvent.setTopicTagIds(q.getTagIds());
            reviewEvent.setSource(ReviewSource.EXAM_SUBMISSION);

            events.add(reviewEvent);
        }

        studyReviewEventRepository.saveAll(events);
        log.info("Saved {} StudyReviewEvents for attemptId={}", events.size(), event.getAttemptId());
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
