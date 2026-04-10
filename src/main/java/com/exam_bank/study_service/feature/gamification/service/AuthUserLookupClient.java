package com.exam_bank.study_service.feature.gamification.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUserLookupClient {

    private final RestTemplate restTemplate;

    @Value("${auth.service.base-url:http://localhost:8080}")
    private String authServiceBaseUrl;

    @Value("${auth.service.internal-token:change-me-secret}")
    private String internalToken;

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

        String url = authServiceBaseUrl + "/api/v1/auth/internal/users/display-names";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserDisplayNameBatchRequest> requestEntity = new HttpEntity<>(
                new UserDisplayNameBatchRequest(normalizedUserIds),
                headers);

        try {
            ResponseEntity<List<UserDisplayNameResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<List<UserDisplayNameResponse>>() {
                    });

            List<UserDisplayNameResponse> body = response.getBody() != null ? response.getBody() : List.of();
            return body.stream()
                    .filter(item -> item.userId() != null && item.userId() > 0 && hasText(item.fullName()))
                    .collect(Collectors.toMap(
                            UserDisplayNameResponse::userId,
                            item -> item.fullName().trim(),
                            (left, right) -> left,
                            LinkedHashMap::new));
        } catch (Exception exception) {
            log.warn("Failed to resolve display names from auth_service: {}", exception.getMessage());
            return Map.of();
        }
    }

    private record UserDisplayNameBatchRequest(List<Long> userIds) {
    }

    private record UserDisplayNameResponse(Long userId, String fullName) {
    }
}
