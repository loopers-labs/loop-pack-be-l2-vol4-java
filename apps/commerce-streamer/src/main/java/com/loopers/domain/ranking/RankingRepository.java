package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Map;

/**
 * 랭킹 보드 저장소 포트. 응용(Collect·Score)은 이 추상에만 의존하고 Redis 는 인프라 어댑터가 안다.
 */
public interface RankingRepository {

    /**
     * raw 신호 보드에 델타를 누적한다 (Collect).
     * 음수 델타 허용 — LIKE 취소(-1)가 대표. 음수 점수는 클램프하지 않는다(취소 반영의 회계 흔적).
     */
    void increment(RankingSignal signal, LocalDate date, long productId, double delta);

    /**
     * raw 보드들을 가중 합산해 display 보드를 만든다 (Score).
     * destination 을 덮어쓰는 재계산이므로 몇 번을 재실행해도 같은 결과(멱등) — 다중 인스턴스 무해.
     */
    void compose(LocalDate date, Map<RankingSignal, Double> weights);

    /**
     * from 날짜 raw 점수 × weight 를 to 날짜 raw 보드에 시드한다 (콜드 스타트 완화).
     * to 보드의 기존 누적분은 보존하며, 마커 가드로 to 날짜당 1회만 실행된다.
     *
     * @return 시드를 수행했으면 true, 이미 수행돼 건너뛰었으면 false
     */
    boolean carryOver(LocalDate from, LocalDate to, double weight);
}
