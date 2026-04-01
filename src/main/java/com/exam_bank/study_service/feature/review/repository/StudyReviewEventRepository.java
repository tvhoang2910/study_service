package com.exam_bank.study_service.feature.review.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.review.entity.StudyReviewEvent;

@Repository
public interface StudyReviewEventRepository extends JpaRepository<StudyReviewEvent, Long> {

    @Query("select e from StudyReviewEvent e where e.attemptId = :attemptId and e.itemId = :itemId")
    Optional<StudyReviewEvent> findByAttemptIdAndItemId(
            @Param("attemptId") Long attemptId,
            @Param("itemId") Long itemId);
}
