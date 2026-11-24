package com.figlms.identity.internal;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Temporary login for the first endpoints (#6): HTTP Basic with the sample users of {@code infra/sample-data}.
 *
 * <p>Usernames and roles follow the Moodle sample data (student1..student5, teacher1, teacher2, admin). All share the
 * password {@code fig.sample-users.password} (from {@code SAMPLE_USER_PASSWORD}); when it is empty no user exists and
 * every API call gets 401. The identity module replaces this with real accounts and tokens later.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService sampleUsers(@Value("${fig.sample-users.password:}") String password, PasswordEncoder encoder) {
        if (password.isBlank()) {
            return new InMemoryUserDetailsManager();
        }
        String hash = encoder.encode(password);
        List<UserDetails> users = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            users.add(User.withUsername("student" + i).password(hash).roles("STUDENT").build());
        }
        users.add(User.withUsername("teacher1").password(hash).roles("TEACHER").build());
        users.add(User.withUsername("teacher2").password(hash).roles("TEACHER").build());
        users.add(User.withUsername("admin").password(hash).roles("ADMIN").build());
        return new InMemoryUserDetailsManager(users);
    }
}
