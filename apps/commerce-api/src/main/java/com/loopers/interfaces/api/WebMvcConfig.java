package com.loopers.interfaces.api;

import com.loopers.interfaces.api.admin.AdminArgumentResolver;
import com.loopers.interfaces.api.queue.EntryTokenInterceptor;
import com.loopers.interfaces.api.user.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final AdminArgumentResolver adminArgumentResolver;
    private final EntryTokenInterceptor entryTokenInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
        resolvers.add(adminArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 주문 접수(POST /api/v1/orders)에만 입장 토큰 관문을 건다.
        registry.addInterceptor(entryTokenInterceptor).addPathPatterns("/api/v1/orders");
    }
}
