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

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LikeApplicationService 단위 테스트.
 *
 * <p>좋아요 등록/취소의 멱등성과, 좋아요 수 비정규화(like_count) 동기화 흐름을 검증한다.
 * 핵심 원칙: likes 테이블이 실제로 변경된 경우에만 like_count 를 갱신한다.
 *
 * <p>Mockito 로 Repository 를 대체하여 Spring 컨텍스트 없이 단위 테스트로 구성한다.
 */
class LikeApplicationServiceTest {

    private LikeRepository likeRepository;
    private ProductRepository productRepository;
    private StockRepository stockRepository;
    private LikeApplicationService sut;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productRepository = mock(ProductRepository.class);
        stockRepository = mock(StockRepository.class);
        sut = new LikeApplicationService(likeRepository, productRepository, stockRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("정상 등록되면 saveAndFlush 와 like_count 증가가 함께 호출된다.")
        @Test
        void savesLikeAndIncrementsCount_whenNotYetLiked() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);

            // act
            sut.like(10L, 100L);

            // assert — INSERT 성공 경로에서만 like_count + 1
            verify(likeRepository, times(1)).saveAndFlush(any(LikeModel.class));
            verify(productRepository, times(1)).increaseLikeCount(100L);
        }

        @DisplayName("이미 좋아요한 경우 saveAndFlush 도 like_count 증가도 일어나지 않는다 (멱등 - P-1).")
        @Test
        void doesNothing_whenAlreadyLiked() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(true);

            // act
            sut.like(10L, 100L);

            // assert
            verify(likeRepository, never()).saveAndFlush(any());
            verify(productRepository, never()).increaseLikeCount(anyLong());
        }

        @DisplayName("동시 요청 UK 위반 시 정상 종료하며 like_count 는 증가하지 않는다 (멱등 최후 방어선).")
        @Test
        void recoversFromUkViolation_withoutIncrement() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);
            SQLIntegrityConstraintViolationException sqlEx =
                new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062);
            when(likeRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation", sqlEx));

            // act & assert — likes 무변경이므로 count 도 건드리지 않음
            assertDoesNotThrow(() -> sut.like(10L, 100L));
            verify(productRepository, never()).increaseLikeCount(anyLong());
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
            verify(productRepository, never()).increaseLikeCount(anyLong());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("실제 삭제되면 like_count 감소가 함께 호출된다.")
        @Test
        void deletesAndDecrementsCount_whenLikeExists() {
            // arrange — delete 가 1행 삭제(실제 좋아요 존재)
            when(likeRepository.delete(10L, 100L)).thenReturn(1);

            // act
            sut.unlike(10L, 100L);

            // assert
            verify(likeRepository, times(1)).delete(10L, 100L);
            verify(productRepository, times(1)).decreaseLikeCount(100L);
        }

        @DisplayName("좋아요가 없으면(0행 삭제) like_count 는 감소하지 않는다 (멱등 - P-2).")
        @Test
        void doesNotDecrement_whenNoLikeExists() {
            // arrange — delete 가 0행(원래 없던 좋아요)
            when(likeRepository.delete(10L, 100L)).thenReturn(0);

            // act & assert
            assertDoesNotThrow(() -> sut.unlike(10L, 100L));
            verify(productRepository, never()).decreaseLikeCount(anyLong());
        }
    }
}
