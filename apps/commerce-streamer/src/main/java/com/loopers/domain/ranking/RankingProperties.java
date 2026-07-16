package com.loopers.domain.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 랭킹 가중치·TTL 설정 — 코드 수정 없이 yml 로 조절한다. (@ConfigurationPropertiesScan 으로 자동 등록) */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    @DefaultValue("0.1") double viewWeight,
    @DefaultValue("0.2") double likeWeight,
    @DefaultValue("0.7") double orderWeight,
    @DefaultValue("2") int ttlDays,
    @DefaultValue("0.1") double carryOverRate
) {}
