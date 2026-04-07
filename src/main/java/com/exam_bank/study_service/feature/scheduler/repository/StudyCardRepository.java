package com.exam_bank.study_service.feature.scheduler.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.exam_bank.study_service.feature.scheduler.entity.StudyCard;

@Repository
public interface StudyCardRepository extends JpaRepository<StudyCard, Long> {

    Optional<StudyCard> findByUserIdAndItemId(Long userId, Long itemId);

    List<StudyCard> findByUserIdAndItemIdIn(Long userId, List<Long> itemIds);

        List<StudyCard> findByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            Long userId,
            Integer repetitionThreshold,
            Instant dueAt,
            Pageable pageable);

        long countByUserIdAndRepetitionLessThanAndNextReviewAtLessThanEqual(
            Long userId,
            Integer repetitionThreshold,
            Instant dueAt);
}
