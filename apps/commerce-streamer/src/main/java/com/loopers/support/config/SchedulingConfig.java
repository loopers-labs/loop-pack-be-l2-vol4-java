package com.loopers.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 (streamer 첫 도입 — 랭킹 합성·carry-over 잡).
 * 개별 잡의 등록 여부는 각 스케줄러의 @ConditionalOnProperty 토글이 결정한다(test 프로필 off).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
