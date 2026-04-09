package com.exam_bank.study_service.feature.gamification.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.gamification.entity.UserStreakStatus;

@Repository
public interface UserStreakStatusRepository extends JpaRepository<UserStreakStatus, Long> {

    Optional<UserStreakStatus> findByUserId(Long userId);
}
