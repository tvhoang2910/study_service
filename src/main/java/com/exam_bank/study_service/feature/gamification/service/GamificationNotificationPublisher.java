package com.exam_bank.study_service.feature.gamification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.exam_bank.study_service.dto.message.AdminAlertMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationNotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${notification.exchange:notification.events}")
    private String notificationExchange;

    @Value("${notification.admin-alert-routing-key:notification.send.admin.alert}")
    private String adminAlertRoutingKey;

    public void publishAchievementUnlocked(Long userId, List<String> achievementNames) {
        if (userId == null || userId <= 0 || achievementNames == null || achievementNames.isEmpty()) {
            return;
        }

        String title = achievementNames.size() == 1
                ? "Bạn đã đạt thành tựu mới"
                : "Bạn đã đạt thêm " + achievementNames.size() + " thành tựu";

        String body = achievementNames.size() == 1
                ? achievementNames.getFirst()
                : String.join(", ", achievementNames);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", userId);
        metadata.put("achievementNames", achievementNames);

        AdminAlertMessage message = new AdminAlertMessage(
                "ACHIEVEMENT_UNLOCKED",
                title,
                body,
                List.of(),
                "/dashboard/gamification",
                metadata);

        try {
            rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
            log.info("Published achievement web-push alert userId={} count={}", userId, achievementNames.size());
        } catch (AmqpException exception) {
            log.error("Failed to publish achievement web-push alert for userId={}", userId, exception);
        }
    }

    public void publishStreakQualified(Long userId, int streakDays, int todayStudyMinutes) {
        if (userId == null || userId <= 0) {
            return;
        }

        String title = "Bạn vừa tăng streak";
        String body = String.format("Bạn đã đạt streak %d ngày liên tiếp (%d phút hôm nay).", streakDays,
                todayStudyMinutes);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", userId);
        metadata.put("streakDays", streakDays);
        metadata.put("todayStudyMinutes", todayStudyMinutes);

        AdminAlertMessage message = new AdminAlertMessage(
                "STREAK_QUALIFIED",
                title,
                body,
                List.of(),
                "/dashboard/gamification",
                metadata);

        try {
            rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
            log.info("Published streak web-push alert userId={} streakDays={}", userId, streakDays);
        } catch (AmqpException exception) {
            log.error("Failed to publish streak web-push alert for userId={}", userId, exception);
        }
    }
}
