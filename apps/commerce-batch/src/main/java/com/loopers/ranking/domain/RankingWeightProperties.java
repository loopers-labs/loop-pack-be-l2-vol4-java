package com.loopers.ranking.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 점수 가중치. application.yml의 ranking.weight로 바인딩된다.
 * 상수 대신 설정으로 빼 재배포 없이(설정 변경/재기동) 가중치를 조절할 수 있게 한다.
 * (streamer의 동일 이름 설정과 같은 키 — 앱이 분리돼 있어 각자 보유한다)
 */
@ConfigurationProperties("ranking.weight")
public record RankingWeightProperties(double view, double like, double order) {
}
