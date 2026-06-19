package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductStatsServiceTest {

    private ProductStatsService productStatsService;
    private ProductStatsRepository productStatsRepository;

    @BeforeEach
    void setUp() {
        productStatsRepository = mock(ProductStatsRepository.class);
        productStatsService = new ProductStatsService(productStatsRepository);
    }

    @DisplayName("상품 통계를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("상품이 주어지면 생성된 통계가 반환된다.")
        @Test
        void createsStats_whenProductIsProvided() {
            // given
            ProductModel product = new ProductModel(1L, "테스트 상품", BigDecimal.valueOf(10000));
            ProductStatsModel expected = new ProductStatsModel(product);
            when(productStatsRepository.save(any(ProductStatsModel.class))).thenReturn(expected);

            // when
            ProductStatsModel result = productStatsService.create(product);

            // then
            assertThat(result).isSameAs(expected);
        }
    }

    @DisplayName("상품 통계를 상품으로 조회할 때,")
    @Nested
    class GetByProduct {

        @DisplayName("존재하는 상품이라면 통계가 반환된다.")
        @Test
        void returnsStats_whenProductExists() {
            // given
            ProductModel product = new ProductModel(1L, "상품", BigDecimal.valueOf(10000));
            ProductStatsModel stats = new ProductStatsModel(product);
            when(productStatsRepository.findByProduct(product)).thenReturn(Optional.of(stats));

            // when
            ProductStatsModel result = productStatsService.getByProduct(product);

            // then
            assertThat(result).isSameAs(stats);
        }

        @DisplayName("존재하지 않는 상품이라면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // given
            ProductModel product = new ProductModel(1L, "상품", BigDecimal.valueOf(10000));
            when(productStatsRepository.findByProduct(product)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productStatsService.getByProduct(product));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 통계를 soft delete할 때,")
    @Nested
    class SoftDelete {

        @DisplayName("존재하는 상품이라면 통계가 soft delete된다.")
        @Test
        void softDeletesStats_whenProductExists() {
            // given
            ProductModel product = new ProductModel(1L, "상품", BigDecimal.valueOf(10000));
            ProductStatsModel stats = new ProductStatsModel(product);
            when(productStatsRepository.findByProduct(product)).thenReturn(Optional.of(stats));
            when(productStatsRepository.save(stats)).thenReturn(stats);

            // when
            productStatsService.delete(product);

            // then
            assertThat(stats.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 상품이라면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // given
            ProductModel product = new ProductModel(1L, "상품", BigDecimal.valueOf(10000));
            when(productStatsRepository.findByProduct(product)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productStatsService.delete(product));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 ID 목록으로 통계 맵을 조회할 때,")
    @Nested
    class GetMapByProductIds {

        @DisplayName("상품 ID 목록이 주어지면 productId를 키로 하는 통계 맵이 반환된다.")
        @Test
        void returnsStatsMap_whenProductIdsAreProvided() {
            // given
            Long productId = 1L;
            ProductModel product = mock(ProductModel.class);
            when(product.getId()).thenReturn(productId);
            ProductStatsModel stats = new ProductStatsModel(product);
            when(productStatsRepository.findAllByProductIds(List.of(productId))).thenReturn(List.of(stats));

            // when
            Map<Long, ProductStatsModel> result = productStatsService.getMapByProductIds(Set.of(productId));

            // then
            assertAll(
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result.get(productId)).isSameAs(stats)
            );
        }
    }

    @DisplayName("좋아요 순으로 통계 페이지를 조회할 때,")
    @Nested
    class FindPage {

        @DisplayName("페이지 정보가 주어지면 통계 페이지가 반환된다.")
        @Test
        void returnsStatsPage_whenPageableIsProvided() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<ProductStatsModel> expected = new PageImpl<>(List.of());
            when(productStatsRepository.findPageOrderByLikeCountDesc(pageable)).thenReturn(expected);

            // when
            Page<ProductStatsModel> result = productStatsService.findPage(pageable);

            // then
            assertThat(result).isSameAs(expected);
        }
    }

    @DisplayName("상품 ID 목록으로 좋아요 순 통계 페이지를 조회할 때,")
    @Nested
    class FindPageByProductIds {

        @DisplayName("상품 ID 목록과 페이지 정보가 주어지면 통계 페이지가 반환된다.")
        @Test
        void returnsStatsPage_whenProductIdsAndPageableAreProvided() {
            // given
            List<Long> productIds = List.of(1L, 2L);
            Pageable pageable = PageRequest.of(0, 20);
            Page<ProductStatsModel> expected = new PageImpl<>(List.of());
            when(productStatsRepository.findPageByProductIdsOrderByLikeCountDesc(productIds, pageable)).thenReturn(expected);

            // when
            Page<ProductStatsModel> result = productStatsService.findPageByProductIds(productIds, pageable);

            // then
            assertThat(result).isSameAs(expected);
        }
    }
}
