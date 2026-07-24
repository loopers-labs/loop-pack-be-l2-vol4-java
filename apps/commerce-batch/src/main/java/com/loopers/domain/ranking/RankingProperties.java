package com.loopers.domain.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 랭킹 집계 설정. 가중치는 commerce-streamer 의 실시간 랭킹과 동일한 기본값을 갖는다(계약).
 *
 * @param topN      MV 에 남길 상위 랭킹 개수
 * @param chunkSize 청크 크기 = Reader 페이지 크기. 트랜잭션 단위이자 한 번에 쥐는 메모리 상한이다.
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    @DefaultValue("0.1") double viewWeight,
    @DefaultValue("0.2") double likeWeight,
    @DefaultValue("0.7") double orderWeight,
    @DefaultValue("100") int topN,
    @DefaultValue("500") int chunkSize
) {}
