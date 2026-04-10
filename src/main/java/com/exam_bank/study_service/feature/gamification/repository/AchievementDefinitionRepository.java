package com.exam_bank.study_service.feature.gamification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.gamification.entity.AchievementDefinition;

@Repository
public interface AchievementDefinitionRepository extends JpaRepository<AchievementDefinition, Long> {

    Optional<AchievementDefinition> findByCode(String code);

    List<AchievementDefinition> findAllByActiveTrueOrderByGroupNameAscPointsDesc();

    List<AchievementDefinition> findByCodeIn(Collection<String> codes);

    List<AchievementDefinition> findAllByOrderByGroupNameAscPointsDesc();
}
