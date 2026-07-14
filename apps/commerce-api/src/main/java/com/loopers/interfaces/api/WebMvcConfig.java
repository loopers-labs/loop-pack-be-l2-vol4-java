package com.loopers.interfaces.api;

import com.loopers.interfaces.api.auth.AuthInterceptor;
import com.loopers.interfaces.api.auth.CurrentUserArgumentResolver;
import com.loopers.interfaces.api.queue.QueueTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final QueueTokenInterceptor queueTokenInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/v1/users")           // 회원가입은 인증 불필요
            .excludePathPatterns("/api/v1/payments/callback"); // PG 콜백은 인증 헤더 없이 수신

        // AuthInterceptor 다음 순서로 등록 — currentUser 속성이 채워진 뒤 토큰을 검증해야 한다
        registry.addInterceptor(queueTokenInterceptor)
            .addPathPatterns("/api/v1/orders");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
