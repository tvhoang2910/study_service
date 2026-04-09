package com.exam_bank.study_service.feature.gamification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.gamification.entity.AchievementCode;
import com.exam_bank.study_service.feature.gamification.entity.UserAchievement;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    Optional<UserAchievement> findByUserIdAndAchievementCode(Long userId, AchievementCode achievementCode);

    List<UserAchievement> findAllByUserIdAndAchievementCode(Long userId, AchievementCode achievementCode);

    List<UserAchievement> findByUserId(Long userId);

    List<UserAchievement> findTop5ByUserIdOrderByUnlockedAtDesc(Long userId);
}
