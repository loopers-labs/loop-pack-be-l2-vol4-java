package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소스 선택 로직 단위 테스트 — Redis/DB 없이 "어느 쪽을 보는가"만 검증한다.
 * 핵심은 <b>한 날짜의 결과가 두 소스에서 섞이지 않는 것</b>.
 */
class RankingCompositeRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 14);

    private RankingRedisRepository redis;
    private RankingSnapshotRepository snapshot;
    private RankingCompositeRepository composite;

    @BeforeEach
    void setUp() {
        redis = mock(RankingRedisRepository.class);
        snapshot = mock(RankingSnapshotRepository.class);
        composite = new RankingCompositeRepository(redis, snapshot);
    }

    private RankedProduct ranked(long rank, long productId) {
        return new RankedProduct(rank, productId, 1.0);
    }

    @DisplayName("ZSET에 데이터가 있으면 Redis 결과를 그대로 쓰고 스냅샷은 보지 않는다.")
    @Test
    void usesRedisWhenAlive() {
        when(redis.findPage(DATE, 1, 20)).thenReturn(List.of(ranked(1, 10L)));

        List<RankedProduct> page = composite.findPage(DATE, 1, 20);

        assertThat(page).hasSize(1);
        assertThat(page.get(0).productId()).isEqualTo(10L);
        verify(snapshot, never()).findPage(any(), anyInt(), anyInt());
    }

    @DisplayName("ZSET에 그 날짜가 없으면(TTL 만료) 스냅샷에서 읽는다.")
    @Test
    void fallsBackToSnapshotWhenDateExpired() {
        when(redis.findPage(DATE, 1, 20)).thenReturn(List.of());
        when(redis.size(DATE)).thenReturn(0L);
        when(snapshot.findPage(DATE, 1, 20)).thenReturn(List.of(ranked(1, 99L)));

        List<RankedProduct> page = composite.findPage(DATE, 1, 20);

        assertThat(page).hasSize(1);
        assertThat(page.get(0).productId()).isEqualTo(99L);
    }

    @DisplayName("ZSET에 날짜는 있는데 페이지가 범위 밖이면 빈 결과다 — 스냅샷으로 넘어가지 않는다(소스 혼합 방지).")
    @Test
    void doesNotMixSourcesOnPageOverflow() {
        // 그 날짜는 ZSET 에 살아있다(3건). 근데 page=10 이라 Redis 는 빈 결과를 준다.
        when(redis.findPage(DATE, 10, 20)).thenReturn(List.of());
        when(redis.size(DATE)).thenReturn(3L);

        List<RankedProduct> page = composite.findPage(DATE, 10, 20);

        assertThat(page).isEmpty();
        // 여기서 스냅샷을 보면 같은 날짜인데 엉뚱한 페이지가 섞여 나온다
        verify(snapshot, never()).findPage(any(), anyInt(), anyInt());
    }

    @DisplayName("size는 ZSET이 살아있으면 ZCARD를, 없으면 스냅샷 보존 행 수를 돌려준다.")
    @Test
    void sizePicksSource() {
        when(redis.size(DATE)).thenReturn(5L);
        assertThat(composite.size(DATE)).isEqualTo(5L);

        when(redis.size(DATE)).thenReturn(0L);
        when(snapshot.size(DATE)).thenReturn(100L);
        assertThat(composite.size(DATE)).isEqualTo(100L);
    }

    @DisplayName("findRank는 스냅샷으로 폴백하지 않는다 — 상세(hot path)의 헛된 DB 조회를 막는다.")
    @Test
    void findRankNeverFallsBack() {
        when(redis.findRank(DATE, 10L)).thenReturn(Optional.empty());

        Optional<Long> rank = composite.findRank(DATE, 10L);

        assertThat(rank).isEmpty();
        verify(snapshot, never()).size(any());
        verify(snapshot, never()).findPage(any(), anyInt(), anyInt());
    }

    @DisplayName("findRank는 ZSET 순위를 그대로 전달한다.")
    @Test
    void findRankDelegates() {
        when(redis.findRank(eq(DATE), eq(10L))).thenReturn(Optional.of(3L));

        assertThat(composite.findRank(DATE, 10L)).contains(3L);
    }
}
