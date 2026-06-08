package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LikeFacade 단위 테스트.
 *
 * <p>스타일 2 전환에 따라 LikeService 가 흡수되어 Facade 가 유스케이스 흐름·조회·저장·멱등 처리를
 * 모두 책임지게 되었다. 좋아요 등록/취소의 멱등성과 협력 흐름을 검증한다.
 *
 * <p>Mockito 로 Repository 와 StockRepository 를 대체하여 Spring 컨텍스트 없이 단위 테스트로 구성한다.
 */
class LikeFacadeTest {

    private LikeRepository likeRepository;
    private ProductRepository productRepository;
    private StockRepository stockRepository;
    private LikeFacade sut;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productRepository = mock(ProductRepository.class);
        stockRepository = mock(StockRepository.class);
        sut = new LikeFacade(likeRepository, productRepository, stockRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("정상적으로 등록되어 saveAndFlush 가 호출된다.")
        @Test
        void savesLike_whenNotYetLiked() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);

            // act
            sut.like(10L, 100L);

            // assert — saveAndFlush 로 즉시 flush하여 UK 위반을 catch 범위 내에서 처리
            verify(likeRepository, times(1)).saveAndFlush(any(LikeModel.class));
        }

        @DisplayName("이미 좋아요한 경우 saveAndFlush 가 호출되지 않는다 (멱등 - P-1).")
        @Test
        void doesNotSave_whenAlreadyLiked() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(true);

            // act
            sut.like(10L, 100L);

            // assert
            verify(likeRepository, never()).saveAndFlush(any());
        }

        @DisplayName("동시 요청으로 UK 위반이 발생해도 정상 종료된다 (멱등 최후 방어선).")
        @Test
        void recoversFromUkViolation() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);
            when(likeRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation"));

            // act & assert
            assertDoesNotThrow(() -> sut.like(10L, 100L));
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다 (P-3).")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> sut.like(10L, 999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).saveAndFlush(any());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("정상적으로 delete 가 호출된다.")
        @Test
        void deletesLike() {
            // act
            sut.unlike(10L, 100L);

            // assert
            verify(likeRepository, times(1)).delete(10L, 100L);
        }

        @DisplayName("좋아요가 없는 상태에서 취소해도 예외 없이 정상 종료된다 (멱등 - P-2).")
        @Test
        void isIdempotent_whenNoLikeExists() {
            // delete 는 0 row 영향이든 1 row 영향이든 예외 없음

            // act & assert
            assertDoesNotThrow(() -> sut.unlike(10L, 100L));
        }
    }

    @DisplayName("상품의 좋아요 수를 집계할 때,")
    @Nested
    class CountByProductId {

        @DisplayName("Repository 의 count 결과를 그대로 반환한다.")
        @Test
        void returnsCount() {
            // arrange
            when(likeRepository.countByProductId(100L)).thenReturn(42L);

            // act
            long result = sut.countByProductId(100L);

            // assert
            assertThat(result).isEqualTo(42L);
        }
    }
}
