package com.loopers.tddstudy.config;

import com.loopers.tddstudy.interfaces.api.order.EntryTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EntryTokenInterceptor entryTokenInterceptor;

    public WebConfig(EntryTokenInterceptor entryTokenInterceptor) {
        this.entryTokenInterceptor = entryTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(entryTokenInterceptor)
                .addPathPatterns("/api/v1/orders");   // 이 경로에만 토큰 게이트 적용
    }
}
