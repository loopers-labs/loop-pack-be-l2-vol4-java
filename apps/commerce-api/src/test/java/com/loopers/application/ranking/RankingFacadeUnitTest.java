package com.loopers.application.ranking;

import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingMvPeriod;
import com.loopers.domain.ranking.RankingMvReadRepository;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RankingFacadeUnitTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 17);

    @Mock
    private RankingRepository rankingRepository;

    @Mock
    private RankingMvReadRepository rankingMvReadRepository;

    @Mock
    private ProductService productService;

    private RankingFacade rankingFacade;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        rankingFacade = new RankingFacade(rankingRepository, rankingMvReadRepository, productService, clock);
    }

    private Product product(Long id) {
        return new Product(id, 1L, "상품" + id, BigDecimal.valueOf(10_000), 0L, null, null, null);
    }

    @DisplayName("랭킹을 조회할 때,")
    @Nested
    class GetRankings {

        @DisplayName("순위권 상품이 삭제되어 빠지면, 다음 페이지를 추가로 조회해 요청한 size만큼 채운다.")
        @Test
        void backfillsFromNextPage_whenSomeRankedProductsAreDeleted() {
            int size = 3;
            given(rankingRepository.countTotal(TODAY)).willReturn(10L);
            // page 0: productId 2는 삭제되어 상품 조회 결과에서 빠진다.
            given(rankingRepository.findPage(TODAY, 0, size)).willReturn(List.of(
                new RankingItem(1L, 100.0),
                new RankingItem(2L, 90.0),
                new RankingItem(3L, 80.0)
            ));
            given(productService.getProductsByIds(List.of(1L, 2L, 3L)))
                .willReturn(List.of(product(1L), product(3L)));
            // 부족한 1자리를 채우기 위해 page 1을 추가 조회한다.
            given(rankingRepository.findPage(TODAY, 1, size)).willReturn(List.of(
                new RankingItem(4L, 70.0)
            ));
            given(productService.getProductsByIds(List.of(4L))).willReturn(List.of(product(4L)));

            RankingInfo info = rankingFacade.getRankings(TODAY, 0, size);

            assertThat(info.items()).hasSize(size);
            assertThat(info.items()).extracting(RankingInfo.RankingProductInfo::rank)
                .containsExactly(1L, 3L, 4L); // 절대 위치 기준 순위 — 삭제된 2위는 결번으로 유지
            assertThat(info.items()).extracting(RankingInfo.RankingProductInfo::productId)
                .containsExactly(1L, 3L, 4L);
            assertThat(info.totalCount()).isEqualTo(10L);
            then(rankingRepository).should(Mockito.times(2)).findPage(eq(TODAY), anyInt(), eq(size));
        }

        @DisplayName("더 조회할 랭킹 데이터가 없으면, size보다 적은 개수를 그대로 반환한다.")
        @Test
        void returnsFewerThanSize_whenNoMoreDataExists() {
            int size = 5;
            given(rankingRepository.countTotal(TODAY)).willReturn(2L);
            given(rankingRepository.findPage(TODAY, 0, size)).willReturn(List.of(
                new RankingItem(1L, 100.0),
                new RankingItem(2L, 90.0)
            ));
            given(productService.getProductsByIds(List.of(1L, 2L)))
                .willReturn(List.of(product(1L), product(2L)));
            given(rankingRepository.findPage(TODAY, 1, size)).willReturn(List.of());

            RankingInfo info = rankingFacade.getRankings(TODAY, 0, size);

            assertThat(info.items()).hasSize(2);
            then(rankingRepository).should(Mockito.times(2)).findPage(eq(TODAY), anyInt(), eq(size));
        }

        @DisplayName("삭제된 상품이 계속 이어져도, 추가 조회는 안전장치(MAX_BACKFILL_PAGES) 횟수만큼만 수행한다.")
        @Test
        void stopsBackfilling_afterMaxBackfillPages() {
            int size = 3;
            given(rankingRepository.countTotal(TODAY)).willReturn(100L);
            given(rankingRepository.findPage(eq(TODAY), anyInt(), eq(size)))
                .willAnswer(invocation -> {
                    int page = invocation.getArgument(1);
                    return List.of(new RankingItem((long) (100 + page), 1.0));
                });
            given(productService.getProductsByIds(any())).willReturn(List.of()); // 매번 삭제된 상품으로 취급

            RankingInfo info = rankingFacade.getRankings(TODAY, 0, size);

            assertThat(info.items()).isEmpty();
            then(rankingRepository).should(Mockito.times(5)).findPage(eq(TODAY), anyInt(), eq(size));
        }

        @DisplayName("date가 null이면, 오늘 날짜 기준으로 조회한다.")
        @Test
        void usesToday_whenDateIsNull() {
            int size = 2;
            given(rankingRepository.countTotal(TODAY)).willReturn(0L);
            given(rankingRepository.findPage(TODAY, 0, size)).willReturn(List.of());

            RankingInfo info = rankingFacade.getRankings(null, 0, size);

            assertThat(info.date()).isEqualTo(TODAY);
            assertThat(info.items()).isEmpty();
        }
    }

    @DisplayName("주간/월간(MV) 랭킹을 조회할 때,")
    @Nested
    class GetRankingsByPeriod {

        @DisplayName("period/periodKey로 MV 리포지토리를 조회해 상품정보와 함께 반환한다.")
        @Test
        void returnsMvRanking_withProductInfo() {
            int size = 2;
            given(rankingMvReadRepository.countTotal(RankingMvPeriod.WEEKLY, "2026W29")).willReturn(2L);
            given(rankingMvReadRepository.findPage(RankingMvPeriod.WEEKLY, "2026W29", 0, size)).willReturn(List.of(
                new RankingItem(1L, 100.0),
                new RankingItem(2L, 90.0)
            ));
            given(productService.getProductsByIds(List.of(1L, 2L)))
                .willReturn(List.of(product(1L), product(2L)));

            RankingInfo info = rankingFacade.getRankings(RankingMvPeriod.WEEKLY, "2026W29", 0, size);

            assertThat(info.period()).isEqualTo(RankingMvPeriod.WEEKLY);
            assertThat(info.periodKey()).isEqualTo("2026W29");
            assertThat(info.date()).isNull();
            assertThat(info.totalCount()).isEqualTo(2L);
            assertThat(info.items()).extracting(RankingInfo.RankingProductInfo::productId).containsExactly(1L, 2L);
        }

        @DisplayName("순위권 상품이 삭제되어 빠지면, 다음 페이지를 추가로 조회해 요청한 size만큼 채운다.")
        @Test
        void backfillsFromNextPage_whenSomeRankedProductsAreDeleted() {
            int size = 2;
            given(rankingMvReadRepository.countTotal(RankingMvPeriod.WEEKLY, "2026W29")).willReturn(10L);
            given(rankingMvReadRepository.findPage(RankingMvPeriod.WEEKLY, "2026W29", 0, size)).willReturn(List.of(
                new RankingItem(1L, 100.0),
                new RankingItem(2L, 90.0)
            ));
            given(productService.getProductsByIds(List.of(1L, 2L))).willReturn(List.of(product(1L)));
            given(rankingMvReadRepository.findPage(RankingMvPeriod.WEEKLY, "2026W29", 1, size)).willReturn(List.of(
                new RankingItem(3L, 80.0)
            ));
            given(productService.getProductsByIds(List.of(3L))).willReturn(List.of(product(3L)));

            RankingInfo info = rankingFacade.getRankings(RankingMvPeriod.WEEKLY, "2026W29", 0, size);

            assertThat(info.items()).hasSize(size);
            assertThat(info.items()).extracting(RankingInfo.RankingProductInfo::rank).containsExactly(1L, 3L);
            then(rankingMvReadRepository).should(Mockito.times(2))
                .findPage(eq(RankingMvPeriod.WEEKLY), eq("2026W29"), anyInt(), eq(size));
        }
    }
}