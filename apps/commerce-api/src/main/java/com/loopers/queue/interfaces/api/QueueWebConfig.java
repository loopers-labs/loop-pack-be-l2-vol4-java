package com.loopers.queue.interfaces.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 주문 API 앞에 입장 토큰 관문(TokenGuard)을 건다. */
@Configuration
@RequiredArgsConstructor
public class QueueWebConfig implements WebMvcConfigurer {

    private final TokenGuard tokenGuard;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenGuard).addPathPatterns("/api/v1/orders");
    }
}
