package com.loopers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.filter.AuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@RequiredArgsConstructor
@Configuration
public class FilterConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthFilter(userRepository, passwordEncoder, objectMapper));
        registration.addUrlPatterns("/api/v1/users/me");
        registration.addUrlPatterns("/api/v1/users/password");
        return registration;
    }
}
