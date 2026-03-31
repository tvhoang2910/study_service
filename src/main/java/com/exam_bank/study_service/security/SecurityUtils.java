package com.exam_bank.study_service.security;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private static final String USER_ID_CLAIM = "userId";

    public Optional<String> getCurrentUserEmail() {
        return currentJwt().map(Jwt::getSubject).filter(subject -> !subject.isBlank());
    }

    public Optional<Role> getCurrentUserRole() {
        return currentJwt()
                .map(jwt -> jwt.getClaimAsString("role"))
                .filter(role -> role != null && !role.isBlank())
                .flatMap(this::parseRole);
    }

    public Optional<Long> getCurrentUserId() {
        return currentJwt().flatMap(this::extractUserId);
    }

    public Optional<CurrentUser> getCurrentUser() {
        Optional<Long> userId = getCurrentUserId();
        Optional<String> email = getCurrentUserEmail();
        Optional<Role> role = getCurrentUserRole();
        if (userId.isEmpty() || email.isEmpty() || role.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(userId.get(), email.get(), role.get()));
    }

    private Optional<Long> extractUserId(Jwt jwt) {
        Object raw = jwt.getClaims().get(USER_ID_CLAIM);
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? Optional.of(value) : Optional.empty();
        }

        if (raw instanceof String text && !text.isBlank()) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? Optional.of(parsed) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private Optional<Role> parseRole(String role) {
        try {
            return Optional.of(Role.valueOf(role.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }
}