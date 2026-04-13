package com.exam_bank.study_service.feature.gamification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.gamification.entity.UserAchievement;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    Optional<UserAchievement> findByUserIdAndAchievementCode(Long userId, String achievementCode);

    List<UserAchievement> findAllByUserIdAndAchievementCode(Long userId, String achievementCode);

    List<UserAchievement> findByUserId(Long userId);

    List<UserAchievement> findByAchievementCode(String achievementCode);

    List<UserAchievement> findTop5ByUserIdOrderByUnlockedAtDesc(Long userId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                WHERE t.relname = 'user_achievements'
                  AND c.conname = 'user_achievements_achievement_code_check'
                  AND c.contype = 'c'
                  AND pg_get_constraintdef(c.oid) NOT ILIKE CONCAT('%''', :achievementCode, '''%')
            )
            """, nativeQuery = true)
    boolean existsIncompatibleAchievementCodeConstraint(@Param("achievementCode") String achievementCode);
}
