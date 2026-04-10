package com.exam_bank.study_service.feature.gamification.entity;

import com.exam_bank.study_service.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "achievement_definitions", indexes = {
        @Index(name = "idx_achievement_definition_active", columnList = "active"),
        @Index(name = "idx_achievement_definition_group", columnList = "group_name")
})
public class AchievementDefinition extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "icon", nullable = false, length = 80)
    private String icon;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "auto_unlock_rule", length = 80)
    private String autoUnlockRule;

    @Column(name = "rule_type", length = 80)
    private String ruleType;

    @Column(name = "rule_threshold")
    private Integer ruleThreshold;

    @Column(name = "rule_threshold_secondary")
    private Integer ruleThresholdSecondary;

    @Column(name = "rule_config_json", length = 4000)
    private String ruleConfigJson;
}
