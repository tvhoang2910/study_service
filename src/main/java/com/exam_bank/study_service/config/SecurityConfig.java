package com.exam_bank.study_service.config;

import static org.springframework.util.StringUtils.hasText;

import java.util.Base64;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.exam_bank.study_service.config.properties.AuthJwtProperties;

@Configuration
@EnableConfigurationProperties(AuthJwtProperties.class)
public class SecurityConfig {

    private final AuthJwtProperties authJwtProperties;

    public SecurityConfig(AuthJwtProperties authJwtProperties) {
        this.authJwtProperties = authJwtProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(getJwtSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(authJwtProperties.getIssuer());
        OAuth2TokenValidator<Jwt> hasRoleClaim = new JwtClaimValidator<>("role",
                role -> role instanceof String value && hasText(value));
        OAuth2TokenValidator<Jwt> hasUserIdClaim = new JwtClaimValidator<>("userId", claim -> {
            if (claim instanceof Number number) {
                return number.longValue() > 0;
            }
            if (claim instanceof String value) {
                try {
                    return Long.parseLong(value.trim()) > 0;
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
            return false;
        });
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, hasRoleClaim, hasUserIdClaim));
        return decoder;
    }

    @Bean
    public Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString("role");
            Collection<SimpleGrantedAuthority> authorities = hasText(role)
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private SecretKey getJwtSecretKey() {
        String secretBase64 = authJwtProperties.getSecret();
        if (!hasText(secretBase64)) {
            throw new IllegalStateException("auth.jwt.secret must not be empty");
        }
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}