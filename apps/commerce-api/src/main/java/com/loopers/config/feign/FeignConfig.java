package com.loopers.config.feign;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * OpenFeign 활성화. 외부 PG(pg-simulator) 연동 클라이언트가 위치한 infrastructure.payment 패키지를 스캔한다.
 */
@Configuration
@EnableFeignClients(basePackages = "com.loopers.infrastructure.payment")
public class FeignConfig {
}
