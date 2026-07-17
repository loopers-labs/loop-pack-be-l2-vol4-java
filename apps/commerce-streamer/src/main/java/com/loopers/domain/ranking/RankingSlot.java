package com.loopers.domain.ranking;

import java.time.LocalDate;

/**
 * raw 보드의 한 칸 — (신호, 날짜 버킷, 상품). 배치 수집이 리스너 안에서 같은 칸의 델타를
 * 합산(핫 키 압축)할 때의 집계 키다.
 */
public record RankingSlot(RankingSignal signal, LocalDate date, long productId) {
}
