package com.loopers.application.like;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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

import java.util.List;

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
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public LikeFacadeIntegrationTest(
        LikeFacade likeFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeJpaRepository likeJpaRepository,
        ProductJpaRepository productJpaRepository,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeJpaRepository = likeJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
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

    @DisplayName("내 좋아요 목록 조회 시, ")
    @Nested
    class GetMyLikes {

        @DisplayName("본인이 좋아요한 active 상품들만 좋아요 누른 시각 최신순으로 반환된다.")
        @Test
        void returnsLikedActiveProducts_orderedByLikedAtDesc() {
            // given
            Long userId = 1L;
            Long brandId = brandFacade.create("아디다스", "Impossible Is Nothing").id();
            Long firstLikedProductId = productFacade.createProduct("스탠스미스", "데일리 스니커즈", 99_000L, 10, brandId).id();
            Long secondLikedProductId = productFacade.createProduct("울트라부스트", "런닝화", 219_000L, 10, brandId).id();
            likeFacade.like(userId, firstLikedProductId);
            likeFacade.like(userId, secondLikedProductId);

            // when
            List<LikedProductInfo> result = likeFacade.getMyLikes(userId, 0, 20);

            // then
            assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(0).id()).isEqualTo(secondLikedProductId),
                () -> assertThat(result.get(1).id()).isEqualTo(firstLikedProductId)
            );
        }

        @DisplayName("좋아요한 상품이 하나도 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // given
            Long userId = 1L;

            // when
            List<LikedProductInfo> result = likeFacade.getMyLikes(userId, 0, 20);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("좋아요한 후 상품이 soft-delete 되면 목록에서 제외된다.")
        @Test
        void excludesSoftDeletedProduct_fromResult() {
            // given
            Long userId = 1L;
            likeFacade.like(userId, productId);
            softDelete(productId);

            // when
            List<LikedProductInfo> result = likeFacade.getMyLikes(userId, 0, 20);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("상품의 brand 가 soft-delete 되어도 product 가 active 면 목록에 남고, brand 요약은 그대로 노출된다.")
        @Test
        void includesProduct_andExposesBrandSummary_whenBrandIsSoftDeletedButProductIsActive() {
            // given
            Long userId = 1L;
            likeFacade.like(userId, productId);
            Long brandId = productJpaRepository.findById(productId).orElseThrow().getBrandId();
            softDeleteBrand(brandId);

            // when
            List<LikedProductInfo> result = likeFacade.getMyLikes(userId, 0, 20);

            // then
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).id()).isEqualTo(productId),
                () -> assertThat(result.get(0).brand().id()).isEqualTo(brandId),
                () -> assertThat(result.get(0).brand().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("다른 회원이 좋아요한 상품은 내 목록에 섞이지 않는다.")
        @Test
        void doesNotIncludeOtherUsersLikes() {
            // given
            Long me = 1L;
            Long other = 2L;
            Long brandId = brandFacade.create("푸마", "Forever Faster").id();
            Long myProductId = productFacade.createProduct("스웨이드 클래식", "캐주얼", 89_000L, 10, brandId).id();
            Long otherProductId = productFacade.createProduct("RS-X", "스니커즈", 129_000L, 10, brandId).id();
            likeFacade.like(me, myProductId);
            likeFacade.like(other, otherProductId);

            // when
            List<LikedProductInfo> result = likeFacade.getMyLikes(me, 0, 20);

            // then
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).id()).isEqualTo(myProductId)
            );
        }

        @DisplayName("page=0, size=2 로 요청하면, 최신 좋아요 2건만 반환된다 (페이지 슬라이싱).")
        @Test
        void returnsOnlyRequestedSlice_whenPageAndSizeAreSpecified() {
            // given
            Long userId = 1L;
            Long brandId = brandFacade.create("뉴발란스", "Made with love").id();
            Long firstId = productFacade.createProduct("992", "헤리티지", 269_000L, 10, brandId).id();
            Long secondId = productFacade.createProduct("1906R", "Y2K", 219_000L, 10, brandId).id();
            Long thirdId = productFacade.createProduct("530", "데일리", 139_000L, 10, brandId).id();
            likeFacade.like(userId, firstId);
            likeFacade.like(userId, secondId);
            likeFacade.like(userId, thirdId);

            // when
            List<LikedProductInfo> firstPage = likeFacade.getMyLikes(userId, 0, 2);
            List<LikedProductInfo> secondPage = likeFacade.getMyLikes(userId, 1, 2);

            // then
            assertAll(
                () -> assertThat(firstPage).hasSize(2),
                () -> assertThat(firstPage.get(0).id()).isEqualTo(thirdId),
                () -> assertThat(firstPage.get(1).id()).isEqualTo(secondId),
                () -> assertThat(secondPage).hasSize(1),
                () -> assertThat(secondPage.get(0).id()).isEqualTo(firstId)
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

    private void softDeleteBrand(Long brandId) {
        BrandModel brand = brandJpaRepository.findById(brandId).orElseThrow();
        brand.delete();
        brandJpaRepository.save(brand);
    }
}
