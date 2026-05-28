package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * LikeFacade 통합 — Like + Product의 합성 트랜잭션 검증.
 * <p>like row 저장 + 비정규화 카운트(D3) 갱신이 함께 일어나는지, 멱등성·존재검증·취소 흐름까지 모두 검증.</p>
 */
@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(),"후드", "포근함", 49_000L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 합성 시")
    @Nested
    class Like {

        @DisplayName("새 좋아요면 product_like 행이 추가되고 product.likeCount가 1 증가한다")
        @Test
        void persistsLikeAndIncrementsCounter() {
            // when
            likeFacade.like(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertAll(
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isTrue(),
                () -> assertThat(product.getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록해도 멱등으로 likeCount는 1로 유지된다")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            // given
            likeFacade.like(userId, productId);

            // when
            likeFacade.like(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1),
                () -> assertThat(product.getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND가 발생하고 like row도 카운트도 변하지 않는다")
        @Test
        void throwsNotFound_andDoesNothing_whenProductMissing() {
            // when / then
            assertThatThrownBy(() -> likeFacade.like(userId, 999_999L))
                .isInstanceOfSatisfying(CoreException.class, ex ->
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
            assertThat(likeJpaRepository.count()).isZero();
        }
    }

    @DisplayName("좋아요 취소 합성 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 row가 사라지고 likeCount가 1 감소한다")
        @Test
        void deletesLikeAndDecrementsCounter() {
            // given
            likeFacade.like(userId, productId);

            // when
            likeFacade.unlike(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertAll(
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isFalse(),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 (상품은 존재) 멱등으로 likeCount는 0으로 유지된다")
        @Test
        void isIdempotent_whenNothingToUnlike_andProductExists() {
            // when
            likeFacade.unlike(userId, productId);

            // then
            ProductModel product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("존재하지 않는 상품을 unlike하면 NOT_FOUND가 발생한다 (like row 0건 + requireExists)")
        @Test
        void throwsNotFound_whenProductMissing() {
            // when / then
            assertThatThrownBy(() -> likeFacade.unlike(userId, 999_999L))
                .isInstanceOfSatisfying(CoreException.class, ex ->
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
