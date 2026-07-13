package com.loopers.config;

import com.loopers.config.ratelimit.OrderRateLimitInterceptor;
import com.loopers.interfaces.api.queue.QueueUserArgumentResolver;
import com.loopers.interfaces.api.user.AuthUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthUserArgumentResolver authUserArgumentResolver;
    private final QueueUserArgumentResolver queueUserArgumentResolver;
    private final OrderRateLimitInterceptor orderRateLimitInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
        resolvers.add(queueUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(orderRateLimitInterceptor).addPathPatterns("/api/v1/orders");
    }
}
