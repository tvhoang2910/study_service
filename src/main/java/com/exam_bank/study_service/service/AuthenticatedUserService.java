package com.exam_bank.study_service.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.study_service.security.CurrentUser;
import com.exam_bank.study_service.security.Role;
import com.exam_bank.study_service.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final SecurityUtils securityUtils;

    public Long getCurrentUserId() {
        return securityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Missing or invalid userId claim"));
    }

    public Role getCurrentUserRole() {
        return securityUtils.getCurrentUserRole()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Missing or invalid role claim"));
    }

    public CurrentUser getCurrentUser() {
        return securityUtils.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Unauthorized"));
    }
}