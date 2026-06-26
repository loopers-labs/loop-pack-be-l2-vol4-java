package com.loopers.config;

import com.loopers.interfaces.api.admin.AdminInterceptor;
import com.loopers.interfaces.api.user.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns(
                "/api/v1/users",          // 회원가입 (인증 불필요)
                "/api/v1/brands/*",       // 브랜드 상세 조회 (인증 불필요)
                "/api/v1/products",       // 상품 목록 조회 (인증 불필요)
                "/api/v1/products/*",     // 상품 상세 조회 (인증 불필요) — /products/{id}/likes 는 제외되지 않음
                "/api/v1/examples/**",    // 예시 API (테스트용, 인증 불필요)
                "/api/v1/payments/callback" // PG 콜백 (PG 서버가 호출, 인증 불필요)
            );

        registry.addInterceptor(adminInterceptor)
            .addPathPatterns("/api-admin/v1/**");
    }
}
