package com.loopers.config;

import com.loopers.interfaces.api.common.interceptor.AdminAuthInterceptor;
import com.loopers.interfaces.api.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/api/v1/users/me",
                "/api/v1/users/me/password",
                "/api/v1/products/*/likes",
                "/api/v1/users/*/likes",
                "/api/v1/orders",
                "/api/v1/orders/*",
                "/api/v1/orders/*/cancel",
                "/api/v1/coupons/*/issue",
                "/api/v1/users/me/coupons",
                "/api/v1/payments"
            );

        // 어드민 API — LDAP 헤더 검증 (payment은 HMAC 보안이므로 제외)
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns(
                "/api-admin/v1/brands/**",
                "/api-admin/v1/products/**",
                "/api-admin/v1/orders/**",
                "/api-admin/v1/coupons/**"
            );
    }
}
