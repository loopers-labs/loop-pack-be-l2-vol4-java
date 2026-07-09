package com.loopers.interfaces.api.config;

import com.loopers.interfaces.api.auth.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final EntryTokenInterceptor entryTokenInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 대기열 관문은 주문 "생성" 만 대상 — 목록/상세 조회와 admin 경로는 게이트 밖 (POST 여부는 인터셉터가 판별)
        registry.addInterceptor(entryTokenInterceptor)
                .addPathPatterns("/api/v1/orders");
    }
}
