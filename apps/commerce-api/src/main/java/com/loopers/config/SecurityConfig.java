package com.loopers.config;

import com.loopers.user.application.UserService;
import com.loopers.support.auth.AdminAuthenticationFilter;
import com.loopers.support.auth.HeaderAuthenticationFilter;
import com.loopers.support.auth.UnauthorizedEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * X-Loopers-LoginId / X-Loopers-LoginPw 헤더 인증을 적용한다.
     * 보호 경로(GET /api/v1/users/me)만 인증을 요구하고, 그 외(회원가입 등)는 허용한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        UserService userService,
        UnauthorizedEntryPoint unauthorizedEntryPoint
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/password").authenticated()
                .requestMatchers("/api/v1/likes/**").authenticated()
                .requestMatchers("/api/v1/orders/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint))
            .addFilterBefore(new AdminAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new HeaderAuthenticationFilter(userService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
