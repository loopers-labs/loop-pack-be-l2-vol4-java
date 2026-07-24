package com.loopers.application.ranking;

import com.loopers.application.brand.BrandRepository;
import com.loopers.application.product.ProductRepository;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    @InjectMocks
    private RankingFacade rankingFacade;

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @Mock
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Test
    @DisplayName("Redis 랭킹 상품 ID를 상품/브랜드 정보와 조합해 랭킹 페이지를 반환한다.")
    void getRankings_ShouldReturnRankingPageWithProductInfo() {
        // given
        String dateKey = "20260714";
        given(rankingRedisRepository.findRankings(dateKey, 1, 20))
            .willReturn(List.of(new RankingEntry(1L, 1, 10.0)));
        given(rankingRedisRepository.count(dateKey)).willReturn(1L);

        ProductModel product = new ProductModel(10L, "Air Max", new BigDecimal("1000.0000"));
        ReflectionTestUtils.setField(product, "id", 1L);
        given(productRepository.findByIds(List.of(1L))).willReturn(List.of(product));

        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", 10L);
        given(brandRepository.findByIds(List.of(10L))).willReturn(List.of(brand));

        // when
        Page<RankingProductInfo> result = rankingFacade.getRankings(
            RankingPeriod.DAILY,
            dateKey,
            dateKey,
            1,
            20
        );

        // then
        assertThat(result.getContent()).hasSize(1);
        RankingProductInfo info = result.getContent().get(0);
        assertThat(info.rank()).isEqualTo(1);
        assertThat(info.score()).isEqualTo(10.0);
        assertThat(info.productId()).isEqualTo(1L);
        assertThat(info.productName()).isEqualTo("Air Max");
        assertThat(info.brandName()).isEqualTo("Nike");
        assertThat(info.price()).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("Redis ZSET에 남아 있지만 상품 조회 결과에 없는 상품은 응답에서 제외한다.")
    void getRankings_WhenProductMissing_ShouldExcludeProduct() {
        // given
        String dateKey = "20260714";
        given(rankingRedisRepository.findRankings(dateKey, 1, 20))
            .willReturn(List.of(
                new RankingEntry(1L, 1, 10.0),
                new RankingEntry(2L, 2, 9.0)
            ));
        given(rankingRedisRepository.count(dateKey)).willReturn(2L);

        ProductModel product = new ProductModel(10L, "Air Max", new BigDecimal("1000.0000"));
        ReflectionTestUtils.setField(product, "id", 1L);
        given(productRepository.findByIds(List.of(1L, 2L))).willReturn(List.of(product));

        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", 10L);
        given(brandRepository.findByIds(List.of(10L))).willReturn(List.of(brand));

        // when
        Page<RankingProductInfo> result = rankingFacade.getRankings(
            RankingPeriod.DAILY,
            dateKey,
            dateKey,
            1,
            20
        );

        // then
        assertThat(result.getContent()).extracting(RankingProductInfo::productId).containsExactly(1L);
    }

    @Test
    @DisplayName("주간 랭킹은 MV 저장소에서 조회한 상품 ID를 상품/브랜드 정보와 조합해 반환한다.")
    void getRankings_WhenWeekly_ShouldUseSnapshotRepository() {
        // given
        given(rankingSnapshotRepository.findRankings(
            RankingPeriod.WEEKLY,
            "20260720",
            "20260726",
            1,
            20
        )).willReturn(List.of(new RankingEntry(1L, 1, 30.0)));
        given(rankingSnapshotRepository.count(RankingPeriod.WEEKLY, "20260720", "20260726")).willReturn(1L);

        ProductModel product = new ProductModel(10L, "Air Max", new BigDecimal("1000.0000"));
        ReflectionTestUtils.setField(product, "id", 1L);
        given(productRepository.findByIds(List.of(1L))).willReturn(List.of(product));

        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", 10L);
        given(brandRepository.findByIds(List.of(10L))).willReturn(List.of(brand));

        // when
        Page<RankingProductInfo> result = rankingFacade.getRankings(
            RankingPeriod.WEEKLY,
            "20260720",
            "20260726",
            1,
            20
        );

        // then
        assertThat(result.getContent()).extracting(RankingProductInfo::score).containsExactly(30.0);
    }
}
