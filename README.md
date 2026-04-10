# Study Service (exam_bank)

Tai lieu nay mo ta module study_service theo source code hien tai.

## 1. Tong quan

Study Service phu trach hoc tap sau khi nguoi dung lam bai:

- Spaced repetition theo kieu SM-2.
- Analytics hoc tap (weakness radar, score history, stats).
- Gamification (streak, leaderboard, achievements, calendar).
- Consume ExamSubmittedEvent tu exam_service de tao du lieu review.

Context path mac dinh:

- /api/v1/study

Port mac dinh:

- 8085

## 2. Tech stack

- Java 21
- Spring Boot 4.0.5
- Spring MVC + Validation
- Spring Security OAuth2 Resource Server (JWT HS256)
- Spring Data JPA (PostgreSQL)
- Spring Data Redis
- Spring AMQP (RabbitMQ)
- Maven Wrapper (mvnw/mvnw.cmd)

## 3. Runtime requirements

Bat buoc:

- PostgreSQL
- Redis
- RabbitMQ
- JDK 21

Health check:

- GET /api/v1/study/actuator/health (neu da expose endpoint health tu actuator)

## 4. Chay local

### 4.1 Environment variables

```bash
PORT=8085

DATABASE_URL=jdbc:postgresql://localhost:5432/exam_bank_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

JWT_ISSUER=auth_service
JWT_SECRET_BASE64=<same-base64-secret-as-auth-service>

REDIS_HOST=localhost
REDIS_PORT=6379

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

EXAM_EVENTS_EXCHANGE=exam.events
EXAM_EVENTS_ROUTING_KEY=exam.submitted
STUDY_EXAM_SUBMITTED_QUEUE=study.exam-submitted.queue
```

Luu y:

- JWT_ISSUER va JWT_SECRET_BASE64 phai dong bo voi auth_service.
- Queue/exchange/routing key phai trung voi exam_service publisher.

### 4.2 Run service

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Build package:

```powershell
.\mvnw.cmd clean package
```

Run tests:

```powershell
.\mvnw.cmd test
```

## 5. API map

Tat ca endpoint ben duoi la relative path theo context /api/v1/study.

Tat ca endpoint nghiep vu deu can access token hop le.

### 5.1 Spaced repetition

- GET /spaced-repetition/me/due?limit=20
- GET /spaced-repetition/me/exam-decks
- POST /spaced-repetition/me/review

### 5.2 Gamification

- GET /gamification/me/overview
- GET /gamification/me/achievements
- GET /gamification/me/leaderboard?limit=10
- GET /gamification/me/calendar?month=yyyy-MM
- POST /gamification/me/share

### 5.3 Analytics

- GET /analytics/me/weakness-radar
- GET /analytics/me/score-history
- GET /analytics/me/stats
- GET /analytics/questions/{questionId}

## 6. Messaging va event contract

### 6.1 RabbitMQ consumer binding

Study service bind queue de consume exam submitted event:

- Exchange: exam.events
- Routing key: exam.submitted
- Queue: study.exam-submitted.queue

### 6.2 ExamSubmittedEventDto contract

Consumer ExamSubmittedConsumer nhan payload gom:

- attemptId, userId, examId, examTitle
- submittedAt, scoreRaw, scoreMax, scorePercent, durationSeconds
- questions[]:
  - questionId, isCorrect, earnedScore, maxScore
  - selectedOptionIds, correctOptionIds
  - responseTimeMs, answerChangeCount, difficulty, tagIds
- examTags[]: tagId, tagName

Sau khi consume event:

- save StudyReviewEvent
- apply spaced repetition scheduling
- refresh gamification progress

## 7. Security notes

- Service chay stateless.
- Chi /actuator/health, /actuator/health/**, /error la permitAll.
- JWT validator yeu cau issuer hop le + role claim + userId claim > 0.
- CORS hien tai cho phep origin co dinh: http://localhost:5173.

## 8. Test scope hien co

Module dang co test cho:

- SpacedRepetitionServiceTest
- SpacedRepetitionControllerTest
- AnalyticsServiceTest
- GamificationServiceTest
- GamificationControllerTest
- ExamSubmittedConsumerTest
- StudyInfrastructureIntegrationTest
- ExamSubmittedEventContractE2ETest

Luu y:

- StudyInfrastructureIntegrationTest va ExamSubmittedEventContractE2ETest co su dung Testcontainers.

## 9. Source references

Cac file can doc nhanh:

- src/main/resources/application.properties
- src/main/java/com/exam_bank/study_service/config/SecurityConfig.java
- src/main/java/com/exam_bank/study_service/config/RabbitConfig.java
- src/main/java/com/exam_bank/study_service/feature/review/consumer/ExamSubmittedConsumer.java
- src/main/java/com/exam_bank/study_service/feature/scheduler/controller/SpacedRepetitionController.java
- src/main/java/com/exam_bank/study_service/feature/gamification/controller/GamificationController.java
- src/main/java/com/exam_bank/study_service/feature/analytics/controller/AnalyticsController.java
