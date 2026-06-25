package com.loopers.interfaces.auth;

import com.loopers.application.user.UserApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class AuthInterceptorConfig implements WebMvcConfigurer {

    private final UserApplicationService userApplicationService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserAuthInterceptor(userApplicationService))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/users",
                        "/api/v1/brands/*",
                        "/api/v1/products",
                        "/api/v1/products/*",
                        "/api/v1/payments/callback"
                );

        registry.addInterceptor(new AdminAuthInterceptor())
                .addPathPatterns("/api-admin/v1/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }
}
