package com.exam_bank.study_service.feature.gamification.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthUserLookupClient {

    private static final String DISPLAY_NAMES_ENDPOINT = "/api/v1/auth/internal/users/display-names";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final UserProfileCacheService userProfileCacheService;
    private final RestClient restClient;
    private final String internalToken;

    public AuthUserLookupClient(
            UserProfileCacheService userProfileCacheService,
            RestClient.Builder restClientBuilder,
            @Value("${auth.service.base-url:http://localhost:8080}") String authServiceBaseUrl,
            @Value("${notification.internal-token:}") String internalToken) {
        this.userProfileCacheService = userProfileCacheService;
        this.restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(authServiceBaseUrl))
                .build();
        this.internalToken = hasText(internalToken) ? internalToken.trim() : "";
    }

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
        Set<Long> unresolvedUserIds = new LinkedHashSet<>();
        for (Long userId : normalizedUserIds) {
            String displayName = resolveFromCache(userId);
            if (displayName != null) {
                resolvedDisplayNames.put(userId, displayName);
                continue;
            }
            unresolvedUserIds.add(userId);
        }

        Map<Long, String> fetchedDisplayNames = fetchDisplayNamesFromAuthService(unresolvedUserIds);
        for (Long userId : normalizedUserIds) {
            if (resolvedDisplayNames.containsKey(userId)) {
                continue;
            }

            String fetchedName = fetchedDisplayNames.get(userId);
            if (!hasText(fetchedName)) {
                continue;
            }

            String normalizedName = normalizeDisplayName(fetchedName);
            resolvedDisplayNames.put(userId, normalizedName);
            userProfileCacheService.putDisplayName(userId, normalizedName);
        }

        return resolvedDisplayNames;
    }

    private String resolveFromCache(Long userId) {
        return userProfileCacheService.findDisplayName(userId)
                .filter(value -> hasText(value))
                .map(this::normalizeDisplayName)
                .orElse(null);
    }

    private Map<Long, String> fetchDisplayNamesFromAuthService(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty() || !hasText(internalToken)) {
            return Map.of();
        }

        List<Long> payloadUserIds = List.copyOf(userIds);
        try {
            List<InternalUserDisplayNameResponse> response = restClient.post()
                    .uri(DISPLAY_NAMES_ENDPOINT)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(new InternalUserDisplayNameBatchRequest(payloadUserIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalUserDisplayNameResponse>>() {
                    });

            if (response == null || response.isEmpty()) {
                return Map.of();
            }

            Map<Long, String> resolvedDisplayNames = new LinkedHashMap<>();
            for (InternalUserDisplayNameResponse item : response) {
                if (item == null || item.userId() == null || !userIds.contains(item.userId())) {
                    continue;
                }

                if (!hasText(item.fullName())) {
                    continue;
                }

                resolvedDisplayNames.put(item.userId(), normalizeDisplayName(item.fullName()));
            }
            return resolvedDisplayNames;
        } catch (RestClientResponseException ex) {
            log.warn("Failed to resolve leaderboard display names from auth-service: status={} message={}",
                    ex.getStatusCode(),
                    ex.getMessage());
            return Map.of();
        } catch (RestClientException ex) {
            log.warn("Failed to resolve leaderboard display names from auth-service: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (!hasText(baseUrl)) {
            return "http://localhost:8080";
        }

        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return hasText(trimmed) ? trimmed : "http://localhost:8080";
    }

    private String normalizeDisplayName(String displayName) {
        return displayName.trim().replaceAll("\\s+", " ");
    }

    private record InternalUserDisplayNameBatchRequest(Collection<Long> userIds) {
    }

    private record InternalUserDisplayNameResponse(Long userId, String fullName) {
    }
}
