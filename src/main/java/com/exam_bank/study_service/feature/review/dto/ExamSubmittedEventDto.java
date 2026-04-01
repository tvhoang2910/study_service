package com.exam_bank.study_service.feature.review.dto;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExamSubmittedEventDto {

    private Long attemptId;
    private Long userId;
    private Long examId;
    private String examTitle;
    private Instant submittedAt;
    private Double scoreRaw;
    private Double scoreMax;
    private Double scorePercent;
    private Long durationSeconds;
    private List<QuestionAnsweredDto> questions;
    private List<TagInfoDto> examTags;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class QuestionAnsweredDto {
        private Long questionId;
        private Boolean isCorrect;
        private Double earnedScore;
        private Double maxScore;
        private Long responseTimeMs;
        private Integer answerChangeCount;
        private Double difficulty;
        private String tagIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TagInfoDto {
        private Long tagId;
        private String tagName;
    }
}
