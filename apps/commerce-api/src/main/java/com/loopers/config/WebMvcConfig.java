package com.loopers.config;

import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.user.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserService userService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(userService))
            .addPathPatterns("/api/v1/users/me/**")   // 인증이 필요한 경로
            .addPathPatterns("/api/v1/users/me");
    }
}
