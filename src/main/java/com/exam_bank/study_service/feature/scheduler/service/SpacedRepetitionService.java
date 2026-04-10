package com.exam_bank.study_service.feature.scheduler.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.feature.review.entity.ReviewSource;
import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository.LatestWrongQuestionProjection;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;
import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.feature.scheduler.dto.DueCardsResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.DueStudyCardDto;
import com.exam_bank.study_service.feature.scheduler.dto.ManualReviewResponseDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2DeckQuestionDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDeckDto;
import com.exam_bank.study_service.feature.scheduler.dto.Sm2ExamDecksResponseDto;
import com.exam_bank.study_service.feature.scheduler.entity.StudyCard;
import com.exam_bank.study_service.feature.scheduler.entity.StudyCardReviewHistory;
import com.exam_bank.study_service.feature.scheduler.repository.StudyCardRepository;
import com.exam_bank.study_service.feature.scheduler.repository.StudyCardReviewHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpacedRepetitionService {

    private static final int MIN_QUALITY = 0;
    private static final int MAX_QUALITY = 5;
    private static final int DEFAULT_DUE_LIMIT = 20;
    private static final int MAX_DUE_LIMIT = 100;
    private static final double MIN_EASINESS_FACTOR = 1.3d;
    private static final int MASTERY_REPETITION_THRESHOLD = 5;

    private final StudyCardRepository studyCardRepository;
    private final StudyCardReviewHistoryRepository historyRepository;
    private final StudyReviewEventRepository reviewEventRepository;
    private final GamificationService gamificationService;

    @Transactional
    public void applyExamEvents(List<StudyReviewEvent> reviewEvents) {
        for (StudyReviewEvent event : reviewEvents) {
            if (event == null || !Boolean.FALSE.equals(event.getIsCorrect())) {
                continue;
            }
            Instant reviewedAt = event.getEvaluatedAt() != null ? event.getEvaluatedAt() : Instant.now();
            applySm2Review(event, reviewedAt);
        }
    }

    @Transactional(readOnly = true)
    public DueCardsResponseDto getDueCards(Long userId, Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        Instant now = Instant.now();
        List<StudyCard> dueCards = studyCardRepository
                .findByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        userId,
                        MASTERY_REPETITION_THRESHOLD,
                        now,
                        PageRequest.of(0, limit));
        long dueCount = studyCardRepository.countByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqual(
                userId,
                MASTERY_REPETITION_THRESHOLD,
                now);

        List<DueStudyCardDto> cards = dueCards.stream().map(this::toDueCardDto).toList();
        return new DueCardsResponseDto(now, dueCount, limit, cards);
    }

    @Transactional(readOnly = true)
    public Sm2ExamDecksResponseDto getExamDecks(Long userId) {
        Instant now = Instant.now();
        List<LatestWrongQuestionProjection> latestWrongRows = reviewEventRepository
                .findLatestWrongQuestionsByExam(userId);
        if (latestWrongRows.isEmpty()) {
            return new Sm2ExamDecksResponseDto(now, 0, 0, List.of());
        }

        List<Long> itemIds = latestWrongRows.stream().map(LatestWrongQuestionProjection::getItemId).distinct().toList();
        Map<Long, StudyCard> cardsByItemId = studyCardRepository.findByUserIdAndItemIdIn(userId, itemIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(StudyCard::getItemId, c -> c, (a, b) -> a));

        Map<Long, DeckBuilder> builders = new LinkedHashMap<>();
        for (LatestWrongQuestionProjection row : latestWrongRows) {
            StudyCard card = cardsByItemId.get(row.getItemId());
            if (card != null && safeInt(card.getRepetition(), 0) >= MASTERY_REPETITION_THRESHOLD) {
                continue;
            }

            DeckBuilder builder = builders.computeIfAbsent(row.getExamId(),
                    key -> new DeckBuilder(row.getExamId(), row.getExamTitle(), row.getAttemptId(),
                            row.getSubmittedAt()));
            builder.questions().add(toDeckQuestionDto(row, card, now));
        }

        List<Sm2ExamDeckDto> decks = builders.values().stream()
                .filter(builder -> !builder.questions().isEmpty())
                .map(builder -> new Sm2ExamDeckDto(
                        builder.examId(),
                        builder.examTitle(),
                        builder.latestAttemptId(),
                        builder.latestSubmittedAt(),
                        builder.questions().size(),
                        List.copyOf(builder.questions())))
                .toList();

        int totalWrongQuestions = decks.stream().mapToInt(Sm2ExamDeckDto::wrongQuestionCount).sum();
        return new Sm2ExamDecksResponseDto(now, decks.size(), totalWrongQuestions, decks);
    }

    @Transactional
    public ManualReviewResponseDto submitManualReview(
            Long userId,
            Long itemId,
            Boolean isCorrect,
            Long responseTimeMs,
            Integer answerChangeCount) {
        Instant reviewedAt = Instant.now();
        studyCardRepository.findByUserIdAndItemId(userId, itemId)
                .ifPresent(card -> ensureManualReviewIsDue(card, reviewedAt));

        int normalizedQuality = computeQuality(Boolean.TRUE.equals(isCorrect), responseTimeMs, answerChangeCount);

        StudyReviewEvent reviewEvent = new StudyReviewEvent();
        reviewEvent.setUserId(userId);
        reviewEvent.setItemId(itemId);
        reviewEvent.setAttemptId(reviewedAt.toEpochMilli());
        reviewEvent.setExamId(0L);
        reviewEvent.setEvaluatedAt(reviewedAt);
        reviewEvent.setQuality(normalizedQuality);
        reviewEvent.setIsCorrect(Boolean.TRUE.equals(isCorrect));
        reviewEvent.setScoreEarned((double) normalizedQuality);
        reviewEvent.setScoreMax((double) MAX_QUALITY);
        reviewEvent.setScorePercent(Math.round((normalizedQuality / (double) MAX_QUALITY) * 10000.0d) / 100.0d);
        reviewEvent.setLatencyMs(responseTimeMs);
        reviewEvent.setAnswerChangeCount(answerChangeCount != null ? Math.max(0, answerChangeCount) : 0);
        reviewEvent.setSource(ReviewSource.MANUAL_REVIEW);

        StudyReviewEvent saved = reviewEventRepository.save(reviewEvent);
        StudyCard updated = applySm2Review(saved, reviewedAt);
        gamificationService.refreshProgressForReview(userId, reviewedAt);

        return new ManualReviewResponseDto(
                updated.getId(),
                updated.getItemId(),
                normalizedQuality,
                updated.getRepetition(),
                updated.getIntervalDays(),
                updated.getEasinessFactor(),
                updated.getNextReviewAt());
    }

    private void ensureManualReviewIsDue(StudyCard card, Instant reviewedAt) {
        Instant nextReviewAt = card.getNextReviewAt();
        if (nextReviewAt != null && nextReviewAt.isAfter(reviewedAt)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Review is not due yet. Please wait until " + nextReviewAt);
        }
    }

    private StudyCard applySm2Review(StudyReviewEvent event, Instant reviewedAt) {
        StudyCard card = studyCardRepository.findByUserIdAndItemId(event.getUserId(), event.getItemId())
                .orElseGet(() -> buildNewCard(event));

        int prevRepetition = safeInt(card.getRepetition(), 0);
        int prevIntervalDays = safeInt(card.getIntervalDays(), 0);
        double prevEasinessFactor = safeDouble(card.getEasinessFactor(), 2.5d);

        Sm2State nextState = calculateNextState(prevRepetition, prevIntervalDays, prevEasinessFactor,
                clampQuality(event.getQuality()), reviewedAt);

        card.setLastAttemptId(event.getAttemptId());
        if (event.getTopicTagIds() != null && !event.getTopicTagIds().isBlank()) {
            card.setTopicTagIds(event.getTopicTagIds());
        }
        card.setRepetition(nextState.repetition());
        card.setIntervalDays(nextState.intervalDays());
        card.setEasinessFactor(nextState.easinessFactor());
        card.setNextReviewAt(nextState.nextReviewAt());
        card.setLastReviewedAt(reviewedAt);
        card.setLastQuality(clampQuality(event.getQuality()));
        card.setLastIsCorrect(Boolean.TRUE.equals(event.getIsCorrect()));
        card.setTotalReviews(safeInt(card.getTotalReviews(), 0) + 1);
        if (Boolean.TRUE.equals(event.getIsCorrect())) {
            card.setCorrectReviews(safeInt(card.getCorrectReviews(), 0) + 1);
        }

        StudyCard savedCard = studyCardRepository.save(card);
        historyRepository
                .save(buildHistory(savedCard, event, reviewedAt, prevRepetition, prevIntervalDays, prevEasinessFactor,
                        nextState));

        log.debug("SM-2 applied: userId={}, itemId={}, quality={}, repetition={}=>{}, interval={}=>{}, ef={}=>{}",
                event.getUserId(),
                event.getItemId(),
                event.getQuality(),
                prevRepetition,
                nextState.repetition(),
                prevIntervalDays,
                nextState.intervalDays(),
                prevEasinessFactor,
                nextState.easinessFactor());

        return savedCard;
    }

    private StudyCard buildNewCard(StudyReviewEvent event) {
        StudyCard card = new StudyCard();
        card.setUserId(event.getUserId());
        card.setItemId(event.getItemId());
        card.setTopicTagIds(event.getTopicTagIds());
        card.setLastAttemptId(event.getAttemptId());
        card.setRepetition(0);
        card.setIntervalDays(0);
        card.setEasinessFactor(2.5d);
        card.setNextReviewAt(Instant.now());
        card.setTotalReviews(0);
        card.setCorrectReviews(0);
        return card;
    }

    private StudyCardReviewHistory buildHistory(
            StudyCard card,
            StudyReviewEvent event,
            Instant reviewedAt,
            int prevRepetition,
            int prevIntervalDays,
            double prevEasinessFactor,
            Sm2State nextState) {
        StudyCardReviewHistory history = new StudyCardReviewHistory();
        history.setCard(card);
        history.setReviewEventId(event.getId());
        history.setReviewedAt(reviewedAt);
        history.setQuality(clampQuality(event.getQuality()));
        history.setIsCorrect(Boolean.TRUE.equals(event.getIsCorrect()));
        history.setPrevRepetition(prevRepetition);
        history.setPrevIntervalDays(prevIntervalDays);
        history.setPrevEasinessFactor(prevEasinessFactor);
        history.setNextRepetition(nextState.repetition());
        history.setNextIntervalDays(nextState.intervalDays());
        history.setNextEasinessFactor(nextState.easinessFactor());
        history.setNextReviewAt(nextState.nextReviewAt());
        return history;
    }

    private Sm2State calculateNextState(int repetition, int intervalDays, double easinessFactor, int quality,
            Instant reviewedAt) {
        double updatedEasinessFactor = easinessFactor
                + (0.1d - (5.0d - quality) * (0.08d + (5.0d - quality) * 0.02d));
        if (updatedEasinessFactor < MIN_EASINESS_FACTOR) {
            updatedEasinessFactor = MIN_EASINESS_FACTOR;
        }

        int nextRepetition;
        int nextIntervalDays;

        if (quality < 3) {
            nextRepetition = 0;
            nextIntervalDays = 0;
        } else if (repetition <= 0) {
            nextRepetition = 1;
            nextIntervalDays = 1;
        } else if (repetition == 1) {
            nextRepetition = 2;
            nextIntervalDays = 6;
        } else {
            nextRepetition = repetition + 1;
            int baseInterval = Math.max(intervalDays, 1);
            nextIntervalDays = Math.max(1, (int) Math.round(baseInterval * updatedEasinessFactor));
        }

        Instant nextReviewAt = reviewedAt.plus(nextIntervalDays, ChronoUnit.DAYS);
        return new Sm2State(nextRepetition, nextIntervalDays, updatedEasinessFactor, nextReviewAt);
    }

    private DueStudyCardDto toDueCardDto(StudyCard card) {
        return new DueStudyCardDto(
                card.getId(),
                card.getItemId(),
                card.getNextReviewAt(),
                card.getRepetition(),
                card.getIntervalDays(),
                card.getEasinessFactor(),
                card.getLastQuality(),
                card.getLastIsCorrect(),
                card.getTotalReviews(),
                card.getCorrectReviews(),
                card.getTopicTagIds());
    }

    private Sm2DeckQuestionDto toDeckQuestionDto(LatestWrongQuestionProjection row, StudyCard card, Instant now) {
        if (card == null) {
            return new Sm2DeckQuestionDto(
                    row.getItemId(),
                    row.getTopicTagIds(),
                    row.getSelectedOptionIds(),
                    row.getCorrectOptionIds(),
                    0,
                    0,
                    2.5d,
                    now,
                    true,
                    0,
                    0);
        }

        Instant nextReviewAt = card.getNextReviewAt() != null ? card.getNextReviewAt() : now;
        boolean dueNow = !nextReviewAt.isAfter(now);
        return new Sm2DeckQuestionDto(
                row.getItemId(),
                row.getTopicTagIds(),
                row.getSelectedOptionIds(),
                row.getCorrectOptionIds(),
                safeInt(card.getRepetition(), 0),
                safeInt(card.getIntervalDays(), 0),
                safeDouble(card.getEasinessFactor(), 2.5d),
                nextReviewAt,
                dueNow,
                safeInt(card.getTotalReviews(), 0),
                safeInt(card.getCorrectReviews(), 0));
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_DUE_LIMIT;
        }
        return Math.min(requestedLimit, MAX_DUE_LIMIT);
    }

    private int clampQuality(Integer quality) {
        if (quality == null) {
            return MIN_QUALITY;
        }
        if (quality < MIN_QUALITY) {
            return MIN_QUALITY;
        }
        if (quality > MAX_QUALITY) {
            return MAX_QUALITY;
        }
        return quality;
    }

    private int computeQuality(boolean isCorrect, Long responseTimeMs, Integer answerChangeCount) {
        if (!isCorrect) {
            return 0;
        }

        long latency = responseTimeMs != null ? Math.max(0L, responseTimeMs) : Long.MAX_VALUE;
        int changes = answerChangeCount != null ? Math.max(0, answerChangeCount) : 0;

        if (latency <= 15_000L && changes == 0) {
            return 5;
        }
        if (latency <= 30_000L && changes <= 1) {
            return 4;
        }
        return 3;
    }

    private int safeInt(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private double safeDouble(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    public StudyCard getCardForManualReview(Long userId, Long itemId) {
        return studyCardRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "No study card found for itemId=" + itemId + " and userId=" + userId));
    }

    private record Sm2State(
            int repetition,
            int intervalDays,
            double easinessFactor,
            Instant nextReviewAt) {
    }

    private record DeckBuilder(
            Long examId,
            String examTitle,
            Long latestAttemptId,
            Instant latestSubmittedAt,
            List<Sm2DeckQuestionDto> questions) {
        private DeckBuilder(Long examId, String examTitle, Long latestAttemptId, Instant latestSubmittedAt) {
            this(examId, examTitle, latestAttemptId, latestSubmittedAt, new java.util.ArrayList<>());
        }
    }
}
