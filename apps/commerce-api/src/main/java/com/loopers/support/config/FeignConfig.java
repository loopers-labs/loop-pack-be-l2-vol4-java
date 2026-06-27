package com.loopers.support.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Retryer;

@Configuration
@EnableFeignClients(basePackages = "com.loopers")
public class FeignConfig {

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }
}
