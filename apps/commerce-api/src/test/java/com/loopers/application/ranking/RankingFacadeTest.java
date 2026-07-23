package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.ranking.PeriodRankingRepository;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    private RankingFacade facade;

    @Mock private RankingRepository rankingRepository;
    @Mock private PeriodRankingRepository periodRankingRepository;
    @Mock private ProductService productService;

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @BeforeEach
    void setUp() {
        facade = new RankingFacade(rankingRepository, periodRankingRepository, productService);
    }

    private ProductInfo productInfo(Long id) {
        return new ProductInfo(id, "상품" + id, 1_000, "브랜드", 5, 3L);
    }

    @DisplayName("getRankings()를 호출할 때,")
    @Nested
    class GetRankings {

        @DisplayName("ZSET 순서(점수 내림차순)를 유지한 채 상품정보가 결합되고 1-based 순위가 부여된다.")
        @Test
        void aggregatesProductInfo_inZsetOrder() {
            // arrange — ZSET은 3, 1, 2 순서로 반환 (상품 3이 1위)
            given(rankingRepository.findTopProductIds(DATE, 0, 20)).willReturn(List.of(3L, 1L, 2L));
            given(rankingRepository.countRanked(DATE)).willReturn(3L);
            given(productService.getAllByIds(List.of(3L, 1L, 2L)))
                .willReturn(List.of(productInfo(1L), productInfo(2L), productInfo(3L))); // DB 순서는 무관

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.DAILY, DATE, 1, 20);

            // assert
            assertAll(
                () -> assertThat(result.totalCount()).isEqualTo(3),
                () -> assertThat(result.items()).extracting(RankingPageInfo.RankedProduct::rank)
                    .containsExactly(1L, 2L, 3L),
                () -> assertThat(result.items()).extracting(item -> item.product().id())
                    .containsExactly(3L, 1L, 2L)
            );
        }

        @DisplayName("랭킹판에는 있지만 DB에서 삭제된 상품은 목록에서 빠지고, 남은 상품의 순위 번호는 유지된다.")
        @Test
        void skipsDeletedProduct_preservingRankNumbers() {
            // arrange — 상품 1이 삭제됨 (2위)
            given(rankingRepository.findTopProductIds(DATE, 0, 20)).willReturn(List.of(3L, 1L, 2L));
            given(rankingRepository.countRanked(DATE)).willReturn(3L);
            given(productService.getAllByIds(List.of(3L, 1L, 2L)))
                .willReturn(List.of(productInfo(3L), productInfo(2L)));

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.DAILY, DATE, 1, 20);

            // assert — 2위가 비고 1위·3위 번호는 그대로
            assertThat(result.items()).extracting(RankingPageInfo.RankedProduct::rank)
                .containsExactly(1L, 3L);
        }

        @DisplayName("2페이지 조회 시 offset이 반영된 순위 번호(size+1부터)가 부여된다.")
        @Test
        void assignsOffsetBasedRanks_onSecondPage() {
            // arrange — page=2, size=2 → offset 2
            given(rankingRepository.findTopProductIds(DATE, 2, 2)).willReturn(List.of(7L, 8L));
            given(rankingRepository.countRanked(DATE)).willReturn(10L);
            given(productService.getAllByIds(List.of(7L, 8L)))
                .willReturn(List.of(productInfo(7L), productInfo(8L)));

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.DAILY, DATE, 2, 2);

            // assert
            assertThat(result.items()).extracting(RankingPageInfo.RankedProduct::rank)
                .containsExactly(3L, 4L);
        }

        @DisplayName("랭킹판이 비어 있으면(해당 날짜 키 없음) 빈 목록과 totalCount 0을 반환한다.")
        @Test
        void returnsEmptyPage_whenNoRankingExists() {
            // arrange
            given(rankingRepository.findTopProductIds(DATE, 0, 20)).willReturn(List.of());
            given(rankingRepository.countRanked(DATE)).willReturn(0L);
            given(productService.getAllByIds(List.of())).willReturn(List.of());

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.DAILY, DATE, 1, 20);

            // assert
            assertAll(
                () -> assertThat(result.items()).isEmpty(),
                () -> assertThat(result.totalCount()).isZero()
            );
        }

        @DisplayName("page가 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPageIsLessThanOne() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> facade.getRankings(RankingPeriod.DAILY, DATE, 0, 20));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("size가 1 미만이거나 100 초과면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSizeIsOutOfRange() {
            // act & assert
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> facade.getRankings(RankingPeriod.DAILY, DATE, 1, 0)).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> facade.getRankings(RankingPeriod.DAILY, DATE, 1, 101)).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("주간·월간 랭킹을 조회할 때,")
    @Nested
    class GetRankingsByPeriod {

        @DisplayName("WEEKLY는 date로 해석한 스냅샷 기간을 조회하고, 응답 date에 그 기간의 period_end를 채운다.")
        @Test
        void readsFromMvRepository_forWeekly() {
            // arrange — 일간(Redis) 저장소는 건드리지 않고 MV 저장소에서 조회. date가 속한 스냅샷 기간이 해석된다.
            var window = new PeriodRankingRepository.ResolvedPeriod(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 15));
            given(periodRankingRepository.resolvePeriod(RankingPeriod.WEEKLY, DATE)).willReturn(Optional.of(window));
            given(periodRankingRepository.findTopProductIds(RankingPeriod.WEEKLY, window, 0, 20)).willReturn(List.of(5L, 6L));
            given(periodRankingRepository.countRanked(RankingPeriod.WEEKLY, window)).willReturn(2L);
            given(productService.getAllByIds(List.of(5L, 6L)))
                .willReturn(List.of(productInfo(5L), productInfo(6L)));

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.WEEKLY, DATE, 1, 20);

            // assert
            assertAll(
                () -> assertThat(result.period()).isEqualTo(RankingPeriod.WEEKLY),
                () -> assertThat(result.date()).isEqualTo(window.periodEnd()),
                () -> assertThat(result.totalCount()).isEqualTo(2),
                () -> assertThat(result.items()).extracting(RankingPageInfo.RankedProduct::rank)
                    .containsExactly(1L, 2L),
                () -> assertThat(result.items()).extracting(item -> item.product().id())
                    .containsExactly(5L, 6L)
            );
        }

        @DisplayName("date 미지정 시 최신 스냅샷을 해석해 조회한다 (resolvePeriod에 null 전달).")
        @Test
        void readsLatestSnapshot_whenDateIsNull() {
            // arrange — date=null → 최신 스냅샷 해석
            var window = new PeriodRankingRepository.ResolvedPeriod(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 15));
            given(periodRankingRepository.resolvePeriod(RankingPeriod.WEEKLY, null)).willReturn(Optional.of(window));
            given(periodRankingRepository.findTopProductIds(RankingPeriod.WEEKLY, window, 0, 20)).willReturn(List.of(5L));
            given(periodRankingRepository.countRanked(RankingPeriod.WEEKLY, window)).willReturn(1L);
            given(productService.getAllByIds(List.of(5L))).willReturn(List.of(productInfo(5L)));

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.WEEKLY, null, 1, 20);

            // assert
            assertAll(
                () -> assertThat(result.date()).isEqualTo(window.periodEnd()),
                () -> assertThat(result.items()).extracting(item -> item.product().id()).containsExactly(5L)
            );
        }

        @DisplayName("해당 기간 스냅샷이 없으면(해석 결과 없음) 빈 목록과 totalCount 0, date null을 반환한다.")
        @Test
        void returnsEmptyPage_whenNoSnapshotResolved() {
            // arrange — MONTHLY 스냅샷 없음
            given(periodRankingRepository.resolvePeriod(RankingPeriod.MONTHLY, DATE)).willReturn(Optional.empty());

            // act
            RankingPageInfo result = facade.getRankings(RankingPeriod.MONTHLY, DATE, 1, 20);

            // assert — 저장소·상품조회를 더 부르지 않고 빈 페이지
            assertAll(
                () -> assertThat(result.period()).isEqualTo(RankingPeriod.MONTHLY),
                () -> assertThat(result.date()).isNull(),
                () -> assertThat(result.items()).isEmpty(),
                () -> assertThat(result.totalCount()).isZero()
            );
        }
    }

    @DisplayName("getTodayRank()를 호출할 때,")
    @Nested
    class GetTodayRank {

        @DisplayName("오늘 랭킹판의 순위를 그대로 반환한다.")
        @Test
        void returnsRank_whenProductIsRanked() {
            // arrange
            given(rankingRepository.findRank(LocalDate.now(), 10L)).willReturn(Optional.of(5L));

            // act & assert
            assertThat(facade.getTodayRank(10L)).contains(5L);
        }

        @DisplayName("순위에 없으면 empty를 반환한다.")
        @Test
        void returnsEmpty_whenProductIsNotRanked() {
            // arrange
            given(rankingRepository.findRank(LocalDate.now(), 10L)).willReturn(Optional.empty());

            // act & assert
            assertThat(facade.getTodayRank(10L)).isEmpty();
        }
    }
}
