package com.loopers.application.like;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LikeFacadeIntegrationTest {

    private final LikeFacade likeFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final LikeJpaRepository likeJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public LikeFacadeIntegrationTest(
        LikeFacade likeFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeJpaRepository likeJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeJpaRepository = likeJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class Like {

        @DisplayName("신규 Like 면, likes 행이 1개 생성되고 상품의 like_count 가 1 증가한다.")
        @Test
        void persistsLikeAndIncrementsCount_whenNew() {
            // given
            Long userId = 1L;

            // when
            likeFacade.like(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(loadLikeCount(productId)).isEqualTo(1L)
            );
        }

        @DisplayName("같은 (userId, productId) 로 두 번 호출해도, likes 행은 1개이고 like_count 도 1 이다 (멱등).")
        @Test
        void remainsIdempotent_whenSameUserLikesTwice() {
            // given
            Long userId = 1L;

            // when
            likeFacade.like(userId, productId);
            likeFacade.like(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(loadLikeCount(productId)).isEqualTo(1L)
            );
        }

        @DisplayName("다른 두 사용자가 같은 상품에 좋아요를 누르면, likes 행은 2개이고 like_count 는 2 이다.")
        @Test
        void accumulatesIndependently_whenDifferentUsersLikeSameProduct() {
            // given
            Long userA = 1L;
            Long userB = 2L;

            // when
            likeFacade.like(userA, productId);
            likeFacade.like(userB, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isEqualTo(2L),
                () -> assertThat(loadLikeCount(productId)).isEqualTo(2L)
            );
        }

        @DisplayName("soft-deleted 된 상품에 좋아요를 누르면, PRODUCT_NOT_FOUND 예외가 발생하고 likes 와 like_count 가 변하지 않는다.")
        @Test
        void throwsProductNotFound_whenProductIsSoftDeleted() {
            // given
            Long userId = 1L;
            softDelete(productId);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> likeFacade.like(userId, productId));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
                () -> assertThat(likeJpaRepository.count()).isZero(),
                () -> assertThat(loadLikeCount(productId)).isZero()
            );
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class Unlike {

        @DisplayName("존재하는 Like 면, likes 행이 삭제되고 like_count 가 1 감소한다.")
        @Test
        void deletesLikeAndDecrementsCount_whenLikeExists() {
            // given
            Long userId = 1L;
            likeFacade.like(userId, productId);

            // when
            likeFacade.unlike(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isZero(),
                () -> assertThat(loadLikeCount(productId)).isZero()
            );
        }

        @DisplayName("존재하지 않는 Like 를 취소해도, like_count 가 변하지 않는다 (멱등).")
        @Test
        void remainsIdempotent_whenLikeDoesNotExist() {
            // given
            Long userId = 1L;

            // when
            likeFacade.unlike(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isZero(),
                () -> assertThat(loadLikeCount(productId)).isZero()
            );
        }

        @DisplayName("soft-deleted 된 상품의 Like 를 취소하면, PRODUCT_NOT_FOUND 예외가 발생하고 likes 와 like_count 가 변하지 않는다.")
        @Test
        void throwsProductNotFound_whenProductIsSoftDeleted() {
            // given
            Long userId = 1L;
            likeFacade.like(userId, productId);
            softDelete(productId);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> likeFacade.unlike(userId, productId));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(loadLikeCount(productId)).isEqualTo(1L)
            );
        }
    }

    private long loadLikeCount(Long productId) {
        return productJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    private void softDelete(Long productId) {
        ProductModel product = productJpaRepository.findById(productId).orElseThrow();
        product.delete();
        productJpaRepository.save(product);
    }
}
