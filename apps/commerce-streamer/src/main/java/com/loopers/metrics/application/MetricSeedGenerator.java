package com.loopers.metrics.application;

import java.time.LocalDate;

/**
 * 상품×날짜의 시드 카운트를 결정적으로 낸다. 상품별 기본 인기도(id 해시)에 일별 변동(날짜 해시)을 얹어
 * 순위에 편차를 준다. 조회를 가장 많이, 판매를 가장 적게 둬 실제 분포를 흉내낸다. 순수 로직(I/O 없음).
 */
public final class MetricSeedGenerator {

    private MetricSeedGenerator() {
    }

    public static DayCount generate(long productId, LocalDate date) {
        long day = date.toEpochDay();
        long view = Math.floorMod(productId * 7919 + day * 104729, 1000);
        long like = Math.floorMod(productId * 6271 + day * 92831, 200);
        long sales = Math.floorMod(productId * 3571 + day * 51503, 50);
        return new DayCount(view, like, sales);
    }

    public record DayCount(long view, long like, long sales) {
    }
}
