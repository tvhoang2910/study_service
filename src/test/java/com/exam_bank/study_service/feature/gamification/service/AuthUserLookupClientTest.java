package com.exam_bank.study_service.feature.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthUserLookupClientTest {

    private static final String AUTH_DISPLAY_NAMES_URL = "http://auth-service:8080/api/v1/auth/internal/users/display-names";

    private UserProfileCacheService userProfileCacheService;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        userProfileCacheService = new UserProfileCacheService();
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void findDisplayNamesByUserIds_shouldMergeCacheAndAuthFallback_andWarmCache() {
        userProfileCacheService.putDisplayName(1L, "  Nguyễn Văn A  ");

        AuthUserLookupClient client = new AuthUserLookupClient(
                userProfileCacheService,
                restClientBuilder,
                "http://auth-service:8080/",
                "dev-internal-token");

        mockServer.expect(once(), requestTo(AUTH_DISPLAY_NAMES_URL))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andExpect(content().json("{\"userIds\":[2]}"))
                .andRespond(withSuccess(
                        "[{\"userId\":2,\"fullName\":\"  Nguyễn   Văn   B  \"}]",
                        MediaType.APPLICATION_JSON));

        Map<Long, String> result = client.findDisplayNamesByUserIds(Set.of(1L, 2L));

        assertThat(result).containsEntry(1L, "Nguyễn Văn A");
        assertThat(result).containsEntry(2L, "Nguyễn Văn B");
        assertThat(userProfileCacheService.findDisplayName(2L)).contains("Nguyễn Văn B");
        mockServer.verify();
    }

    @Test
    void findDisplayNamesByUserIds_shouldSkipFallback_whenInternalTokenMissing() {
        userProfileCacheService.putDisplayName(1L, "Nguyễn Văn A");

        AuthUserLookupClient client = new AuthUserLookupClient(
                userProfileCacheService,
                restClientBuilder,
                "http://auth-service:8080",
                "  ");

        Map<Long, String> result = client.findDisplayNamesByUserIds(Set.of(1L, 2L));

        assertThat(result).containsOnly(Map.entry(1L, "Nguyễn Văn A"));
        mockServer.verify();
    }

    @Test
    void findDisplayNamesByUserIds_shouldReturnCacheOnly_whenAuthFallbackFails() {
        userProfileCacheService.putDisplayName(1L, "Nguyễn Văn A");

        AuthUserLookupClient client = new AuthUserLookupClient(
                userProfileCacheService,
                restClientBuilder,
                "http://auth-service:8080",
                "dev-internal-token");

        mockServer.expect(once(), requestTo(AUTH_DISPLAY_NAMES_URL))
                .andExpect(method(POST))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andRespond(withServerError());

        Map<Long, String> result = client.findDisplayNamesByUserIds(Set.of(1L, 2L));

        assertThat(result).containsOnly(Map.entry(1L, "Nguyễn Văn A"));
        assertThat(result).doesNotContainKey(2L);
        mockServer.verify();
    }
}
