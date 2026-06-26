package com.loopers.support.config;

import com.loopers.support.interceptor.AdminInterceptor;
import com.loopers.support.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/api/v1/users/me",
                "/api/v1/users/me/**",
                "/api/v1/users/*/likes",
                "/api/v1/products/*/likes",
                "/api/v1/orders",
                "/api/v1/orders/**",
                "/api/v1/coupons/**",
                "/api/v1/payments",
                "/api/v1/payments/*/sync"
            );
        registry.addInterceptor(adminInterceptor)
            .addPathPatterns("/api-admin/v1/**");
    }
}
