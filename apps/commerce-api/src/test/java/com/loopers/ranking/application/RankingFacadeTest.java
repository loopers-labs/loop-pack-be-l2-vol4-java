package com.loopers.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductFacade;
import com.loopers.ranking.domain.MaterializedRankingRepository;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingPage;
import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RankingFacadeTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository = mock(RankingRepository.class);
    private final MaterializedRankingRepository materializedRankingRepository =
            mock(MaterializedRankingRepository.class);
    private final ProductFacade productFacade = mock(ProductFacade.class);
    private final RankingFacade rankingFacade =
            new RankingFacade(
                    rankingRepository, materializedRankingRepository, productFacade);

    @DisplayName("period를 생략하면 기존 Redis 일간 랭킹을 조회한다.")
    @Test
    void routesDefaultPeriodToDailyRedis() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        when(rankingRepository.findPage(date, 1, 20)).thenReturn(RankingPage.empty());
        when(productFacade.getExistingProductDetails(List.of())).thenReturn(List.of());

        RankingPageInfo result =
                rankingFacade.getRankings(null, "20260716", 1, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isZero();
        verify(rankingRepository).findPage(date, 1, 20);
        verify(materializedRankingRepository, never())
                .findPage(RankingPeriod.WEEKLY, date, 1, 20);
    }

    @DisplayName("주간과 월간 랭킹은 해당 날짜의 MV 스냅샷을 조회한다.")
    @Test
    void routesBatchPeriodsToMaterializedSnapshots() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        when(materializedRankingRepository.findPage(
                        RankingPeriod.WEEKLY, date, 1, 20))
                .thenReturn(RankingPage.empty());
        when(materializedRankingRepository.findPage(
                        RankingPeriod.MONTHLY, date, 2, 10))
                .thenReturn(RankingPage.empty());
        when(productFacade.getExistingProductDetails(List.of())).thenReturn(List.of());

        rankingFacade.getRankings("weekly", "20260716", 1, 20);
        rankingFacade.getRankings("monthly", "20260716", 2, 10);

        verify(materializedRankingRepository)
                .findPage(RankingPeriod.WEEKLY, date, 1, 20);
        verify(materializedRankingRepository)
                .findPage(RankingPeriod.MONTHLY, date, 2, 10);
        verify(rankingRepository, never()).findPage(date, 1, 20);
    }

    @DisplayName("날짜를 생략한 주간과 월간 랭킹은 서울 기준 전일 스냅샷을 조회한다.")
    @Test
    void defaultsBatchPeriodsToPreviousSeoulDate() {
        when(materializedRankingRepository.findPage(
                        eq(RankingPeriod.WEEKLY), any(LocalDate.class), eq(1), eq(20)))
                .thenReturn(RankingPage.empty());
        when(materializedRankingRepository.findPage(
                        eq(RankingPeriod.MONTHLY), any(LocalDate.class), eq(1), eq(20)))
                .thenReturn(RankingPage.empty());
        when(productFacade.getExistingProductDetails(List.of())).thenReturn(List.of());
        LocalDate expectedBeforeCall = LocalDate.now(SEOUL_ZONE).minusDays(1);

        rankingFacade.getRankings("weekly", null, 1, 20);
        rankingFacade.getRankings("monthly", null, 1, 20);

        LocalDate expectedAfterCall = LocalDate.now(SEOUL_ZONE).minusDays(1);
        ArgumentCaptor<LocalDate> weeklyDateCaptor =
                ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> monthlyDateCaptor =
                ArgumentCaptor.forClass(LocalDate.class);
        verify(materializedRankingRepository)
                .findPage(
                        eq(RankingPeriod.WEEKLY),
                        weeklyDateCaptor.capture(),
                        eq(1),
                        eq(20));
        verify(materializedRankingRepository)
                .findPage(
                        eq(RankingPeriod.MONTHLY),
                        monthlyDateCaptor.capture(),
                        eq(1),
                        eq(20));
        assertThat(weeklyDateCaptor.getValue())
                .isIn(expectedBeforeCall, expectedAfterCall);
        assertThat(monthlyDateCaptor.getValue())
                .isIn(expectedBeforeCall, expectedAfterCall);
    }

    @DisplayName("배치 조회 결과 순서와 무관하게 ZSET 순서대로 상품을 재조립한다.")
    @Test
    void preservesRankingOrderAfterBatchProductLookup() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        ProductDetailInfo first = product(1L, "1위 상품");
        ProductDetailInfo second = product(2L, "2위 상품");
        when(rankingRepository.findPage(date, 1, 20))
                .thenReturn(
                        new RankingPage(
                                List.of(
                                        new RankingEntry(first.id(), 1L, 3.0D),
                                        new RankingEntry(second.id(), 2L, 2.0D)),
                                2L));
        when(productFacade.getExistingProductDetails(List.of(first.id(), second.id())))
                .thenReturn(List.of(second, first));

        RankingPageInfo result = rankingFacade.getRankings("20260716", 1, 20);

        assertThat(result.items())
                .extracting(item -> item.product().id())
                .containsExactly(first.id(), second.id());
        verify(productFacade).getExistingProductDetails(List.of(first.id(), second.id()));
    }

    @DisplayName("MV 조회 장애는 SERVICE_UNAVAILABLE로 변환하고 원인을 보존한다.")
    @Test
    void translatesMaterializedViewFailure() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        RuntimeException failure = new RuntimeException("database down");
        when(materializedRankingRepository.findPage(
                        RankingPeriod.MONTHLY, date, 1, 20))
                .thenThrow(failure);

        assertThatThrownBy(
                        () ->
                                rankingFacade.getRankings(
                                        "monthly", "20260716", 1, 20))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.SERVICE_UNAVAILABLE))
                .hasCause(failure);
    }

    @DisplayName("랭킹 상품 상세 조회 장애도 SERVICE_UNAVAILABLE로 변환한다.")
    @Test
    void translatesProductHydrationFailure() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        RuntimeException failure = new RuntimeException("product database down");
        when(rankingRepository.findPage(date, 1, 20))
                .thenReturn(
                        new RankingPage(
                                List.of(new RankingEntry(1L, 1L, 99.0D)), 1L));
        when(productFacade.getExistingProductDetails(List.of(1L))).thenThrow(failure);

        assertThatThrownBy(
                        () ->
                                rankingFacade.getRankings(
                                        "daily", "20260716", 1, 20))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.SERVICE_UNAVAILABLE))
                .hasCause(failure);
    }

    @DisplayName("시간 랭킹 상품 상세 조회 장애도 SERVICE_UNAVAILABLE로 변환한다.")
    @Test
    void translatesHourlyProductHydrationFailure() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 7, 16, 13, 0);
        RuntimeException failure = new RuntimeException("product database down");
        when(rankingRepository.findHourlyPage(dateTime, 1, 20))
                .thenReturn(
                        new RankingPage(
                                List.of(new RankingEntry(1L, 1L, 99.0D)), 1L));
        when(productFacade.getExistingProductDetails(List.of(1L))).thenThrow(failure);

        assertThatThrownBy(
                        () ->
                                rankingFacade.getHourlyRankings(
                                        "2026071613", 1, 20))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.SERVICE_UNAVAILABLE))
                .hasCause(failure);
    }

    @DisplayName("잘못된 기간, 날짜와 페이징은 BAD_REQUEST로 거절한다.")
    @Test
    void validatesQueryParameters() {
        assertBadRequest(
                () -> rankingFacade.getRankings("yearly", "20260716", 1, 20));
        assertBadRequest(
                () -> rankingFacade.getRankings("weekly", "20260230", 1, 20));
        assertBadRequest(
                () -> rankingFacade.getRankings("daily", "20260716", 0, 20));
        assertBadRequest(
                () -> rankingFacade.getRankings("daily", "20260716", 1, 101));
    }

    private void assertBadRequest(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.BAD_REQUEST));
    }

    private ProductDetailInfo product(Long id, String name) {
        return new ProductDetailInfo(id, 1L, "브랜드", name, "설명", 1000L, 10, 0L);
    }
}
