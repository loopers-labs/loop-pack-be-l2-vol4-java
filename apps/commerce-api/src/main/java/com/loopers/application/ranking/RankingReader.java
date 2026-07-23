package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * 기간별 랭킹 조회 전략. 하나의 API 가 기간에 따라 서로 다른 저장소로 갈리는 지점을 이 인터페이스로 흡수한다.
 * 세 구현 모두 (productId, score) 를 순위 오름차순으로 반환하므로, Facade 의 상품 hydrate·순위 병합은
 * 저장소 종류를 몰라도 된다(전략 패턴 + Map 라우팅).
 */
public interface RankingReader {

    RankingPeriod period();

    /**
     * 주어진 하루가 속한 기간 버킷의 상위 구간을 순위 순서로 반환한다. page 는 0-based.
     * date→버킷(ISO주/달) 변환 책임은 각 구현이 진다(D5: 서버가 버킷 계산).
     */
    List<RankingEntry> topN(LocalDate date, int page, int size);
}
