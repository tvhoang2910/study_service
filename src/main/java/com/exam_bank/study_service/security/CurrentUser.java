package com.exam_bank.study_service.security;

public record CurrentUser(Long userId, String email, Role role) {
}