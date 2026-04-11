package com.exam_bank.study_service.feature.gamification.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthUserLookupClient {

    private final UserProfileCacheService userProfileCacheService;

    public Map<Long, String> findDisplayNamesByUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<Long> normalizedUserIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> resolvedDisplayNames = new LinkedHashMap<>();
        for (Long userId : normalizedUserIds) {
            userProfileCacheService.findDisplayName(userId)
                    .filter(value -> hasText(value))
                    .map(String::trim)
                    .ifPresent(displayName -> resolvedDisplayNames.put(userId, displayName));
        }

        return resolvedDisplayNames;
    }
}
