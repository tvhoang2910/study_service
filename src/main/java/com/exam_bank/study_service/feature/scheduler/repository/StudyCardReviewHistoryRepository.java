package com.exam_bank.study_service.feature.scheduler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.scheduler.entity.StudyCardReviewHistory;

@Repository
public interface StudyCardReviewHistoryRepository extends JpaRepository<StudyCardReviewHistory, Long> {
}
