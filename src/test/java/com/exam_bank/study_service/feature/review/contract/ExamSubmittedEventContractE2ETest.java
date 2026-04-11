package com.exam_bank.study_service.feature.review.contract;

import com.exam_bank.study_service.feature.gamification.service.GamificationService;
import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;
import com.exam_bank.study_service.feature.review.repository.StudyReviewEventRepository;
import com.exam_bank.study_service.feature.scheduler.service.SpacedRepetitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.lazy-initialization=true",
        "spring.jpa.hibernate.ddl-auto=update"
})
@DisplayName("ExamSubmitted Contract E2E Test")
@SuppressWarnings("resource")
class ExamSubmittedEventContractE2ETest {

    private static final String EXCHANGE_NAME = "exam.events.contract";
    private static final String ROUTING_KEY = "exam.submitted.contract";
    private static final String QUEUE_NAME = "study.exam-submitted.queue.contract";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("study_contract_it")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);

        registry.add("exam.events.exchange", () -> EXCHANGE_NAME);
        registry.add("exam.events.routing-key", () -> ROUTING_KEY);
        registry.add("study.events.exam-submitted.queue", () -> QUEUE_NAME);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StudyReviewEventRepository studyReviewEventRepository;

    @MockitoBean
    private SpacedRepetitionService spacedRepetitionService;

    @MockitoBean
    private GamificationService gamificationService;

    @BeforeEach
    void cleanState() {
        studyReviewEventRepository.deleteAll();
    }

    @Test
    @DisplayName("exam_service contract payload is consumed and persisted by study_service")
    void examServiceContractPayloadIsConsumedAndPersisted() throws Exception {
        Long attemptId = 998877L;
        Long userId = 42L;
        Instant submittedAt = Instant.parse("2026-04-10T07:00:00Z");

        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, buildExamSubmittedContractPayload(attemptId, userId));

        List<StudyReviewEvent> persisted = waitForAttemptEvents(attemptId, 2, Duration.ofSeconds(15));
        assertThat(persisted).hasSize(2);

        StudyReviewEvent first = persisted.stream()
                .filter(event -> Long.valueOf(7001L).equals(event.getItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(first.getUserId()).isEqualTo(userId);
        assertThat(first.getExamId()).isEqualTo(88L);
        assertThat(first.getExamTitle()).isEqualTo("Contract Exam");
        assertThat(first.getQuality()).isEqualTo(5);
        assertThat(first.getScorePercent()).isEqualTo(100.0);
        assertThat(first.getTopicTagIds()).isEqualTo("7,8");

        StudyReviewEvent second = persisted.stream()
                .filter(event -> Long.valueOf(7002L).equals(event.getItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(second.getQuality()).isEqualTo(0);
        assertThat(second.getLatencyMs()).isEqualTo(90000L);
        assertThat(second.getScorePercent()).isEqualTo(0.0);

        verify(spacedRepetitionService).applyExamEvents(anyList());
        verify(gamificationService).refreshProgressForReview(eq(userId), any(Instant.class));

        assertThat(studyReviewEventRepository.findByAttemptIdAndItemId(attemptId, 7001L)).isPresent();
        assertThat(studyReviewEventRepository.findByAttemptIdAndItemId(attemptId, 7002L)).isPresent();
        assertThat(studyReviewEventRepository.findByAttemptIdAndItemId(attemptId, 9999L)).isEmpty();
        assertThat(persisted).allSatisfy(event -> assertThat(event.getEvaluatedAt()).isEqualTo(submittedAt));
    }

    private List<StudyReviewEvent> waitForAttemptEvents(Long attemptId, int expectedCount, Duration timeout)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        List<StudyReviewEvent> matching = List.of();

        while (System.currentTimeMillis() < deadline) {
            matching = studyReviewEventRepository.findAll().stream()
                    .filter(event -> attemptId.equals(event.getAttemptId()))
                    .toList();
            if (matching.size() >= expectedCount) {
                return matching;
            }
            Thread.sleep(200L);
        }

        return matching;
    }

    private Map<String, Object> buildExamSubmittedContractPayload(Long attemptId, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attemptId", attemptId);
        payload.put("userId", userId);
        payload.put("examId", 88L);
        payload.put("examTitle", "Contract Exam");
        payload.put("submittedAt", "2026-04-10T07:00:00Z");
        payload.put("scoreRaw", 7.0);
        payload.put("scoreMax", 10.0);
        payload.put("scorePercent", 70.0);
        payload.put("durationSeconds", 180L);

        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(buildQuestion(
                7001L,
                true,
                1.0,
                1.0,
                "11",
                "11",
                12000L,
                0,
                1.0,
                "7,8"));
        questions.add(buildQuestion(
                7002L,
                false,
                0.0,
                1.0,
                "12",
                "13",
                null,
                2,
                1.0,
                "7,8"));
        payload.put("questions", questions);

        payload.put("examTags", List.of(Map.of("tagId", 7L, "tagName", "Algebra")));
        return payload;
    }

    private Map<String, Object> buildQuestion(
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
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", questionId);
        question.put("isCorrect", isCorrect);
        question.put("earnedScore", earnedScore);
        question.put("maxScore", maxScore);
        question.put("selectedOptionIds", selectedOptionIds);
        question.put("correctOptionIds", correctOptionIds);
        question.put("responseTimeMs", responseTimeMs);
        question.put("answerChangeCount", answerChangeCount);
        question.put("difficulty", difficulty);
        question.put("tagIds", tagIds);
        return question;
    }
}
