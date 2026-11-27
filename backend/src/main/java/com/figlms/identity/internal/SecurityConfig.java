package com.figlms.identity.internal;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Login for the whole application (REQ-IDN-01, REQ-IDN-06, REQ-IDN-07).
 *
 * <ul>
 *   <li>Accounts come from {@code idn_user} ({@link DatabaseUserDetailsService}), passwords are stored as bcrypt hashes.</li>
 *   <li>Two ways to log in: HTTP Basic on each request (scripts, golden master tests), or
 *       {@code POST /api/identity/login}, which opens a session that expires after {@code fig.identity.session-timeout}
 *       of inactivity (SEC-10).</li>
 *   <li>Session requests need the CSRF token from the {@code XSRF-TOKEN} cookie; HTTP Basic requests do not, because
 *       a browser never adds the {@code Authorization} header on its own.</li>
 *   <li>Without login every API answers 401 with the common error body.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
            AuthenticationManager authenticationManager) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        return http
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/identity/login", "/api/identity/password-policy").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/api/identity/login")
                        .ignoringRequestMatchers(request -> request.getHeader("Authorization") != null))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .build();
    }

    /** 401 with the same JSON body as every other error, plus the Basic challenge for scripts. */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, e) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "Basic realm=\"fig-lms\"");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"UNAUTHENTICATED\",\"message\":\"Login required\"}");
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(DatabaseUserDetailsService users, PasswordEncoder encoder,
            org.springframework.context.ApplicationEventPublisher events) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        ProviderManager manager = new ProviderManager(provider);
        manager.setAuthenticationEventPublisher(
                new org.springframework.security.authentication.DefaultAuthenticationEventPublisher(events));
        return manager;
    }

    @Bean
    SecurityContextRepository sessionContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
