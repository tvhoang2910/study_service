package com.exam_bank.study_service.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {

    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;
}