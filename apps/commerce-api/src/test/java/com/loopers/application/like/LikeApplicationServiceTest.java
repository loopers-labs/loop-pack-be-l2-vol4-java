package com.loopers.application.like;

import com.loopers.application.activity.UserActionEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
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
 * LikeApplicationService 단위 테스트.
 *
 * <p>좋아요 등록/취소의 멱등성과, <strong>집계 분리(Round 7)</strong> 흐름을 검증한다.
 * 좋아요 수(like_count) 증감은 더 이상 인라인으로 호출되지 않고, likes 테이블이 실제로 변경된 경우에만
 * {@link LikeChangedEvent}(집계)와 {@link UserActionEvent}(로깅)가 발행된다. 실제 집계는 커밋 후 리스너가 반영한다.
 *
 * <p>Mockito 로 Repository/이벤트 발행기를 대체하여 Spring 컨텍스트 없이 단위 테스트로 구성한다.
 */
class LikeApplicationServiceTest {

    private LikeRepository likeRepository;
    private ProductRepository productRepository;
    private StockRepository stockRepository;
    private ApplicationEventPublisher eventPublisher;
    private LikeApplicationService sut;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productRepository = mock(ProductRepository.class);
        stockRepository = mock(StockRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        sut = new LikeApplicationService(likeRepository, productRepository, stockRepository, eventPublisher);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("정상 등록되면 saveAndFlush 와 함께 집계·로깅 이벤트가 발행된다.")
        @Test
        void savesLikeAndPublishesEvents_whenNotYetLiked() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);

            // act
            sut.like(10L, 100L);

            // assert — INSERT 성공 경로에서만 이벤트 발행 (집계는 리스너가 커밋 후 반영)
            verify(likeRepository, times(1)).saveAndFlush(any(LikeModel.class));
            verify(eventPublisher, times(1)).publishEvent(LikeChangedEvent.liked(100L));
            verify(eventPublisher, times(1)).publishEvent(UserActionEvent.like(10L, 100L));
            // 인라인 집계는 더 이상 호출되지 않는다
            verify(productRepository, never()).increaseLikeCount(100L);
        }

        @DisplayName("이미 좋아요한 경우 saveAndFlush 도 이벤트 발행도 일어나지 않는다 (멱등 - P-1).")
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
            verify(eventPublisher, never()).publishEvent(any());
        }

        @DisplayName("동시 요청 UK 위반 시 정상 종료하며 이벤트도 발행되지 않는다 (멱등 최후 방어선).")
        @Test
        void recoversFromUkViolation_withoutEvent() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);
            SQLIntegrityConstraintViolationException sqlEx =
                new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062);
            when(likeRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation", sqlEx));

            // act & assert — likes 무변경이므로 이벤트도 발행하지 않음
            assertDoesNotThrow(() -> sut.like(10L, 100L));
            verify(eventPublisher, never()).publishEvent(any());
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
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("실제 삭제되면 집계·로깅 이벤트가 발행된다.")
        @Test
        void deletesAndPublishesEvents_whenLikeExists() {
            // arrange — delete 가 1행 삭제(실제 좋아요 존재)
            when(likeRepository.delete(10L, 100L)).thenReturn(1);

            // act
            sut.unlike(10L, 100L);

            // assert
            verify(likeRepository, times(1)).delete(10L, 100L);
            verify(eventPublisher, times(1)).publishEvent(LikeChangedEvent.unliked(100L));
            verify(eventPublisher, times(1)).publishEvent(UserActionEvent.unlike(10L, 100L));
            verify(productRepository, never()).decreaseLikeCount(100L);
        }

        @DisplayName("좋아요가 없으면(0행 삭제) 이벤트가 발행되지 않는다 (멱등 - P-2).")
        @Test
        void doesNotPublish_whenNoLikeExists() {
            // arrange — delete 가 0행(원래 없던 좋아요)
            when(likeRepository.delete(10L, 100L)).thenReturn(0);

            // act & assert
            assertDoesNotThrow(() -> sut.unlike(10L, 100L));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
