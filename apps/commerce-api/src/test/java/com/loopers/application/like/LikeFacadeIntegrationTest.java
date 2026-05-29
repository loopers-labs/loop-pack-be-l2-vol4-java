package com.loopers.application.like;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserEntity;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandFacade brandFacade;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private UserService userService;

    @Autowired
    private LikeService likeService;

    @SpyBean
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        Mockito.reset(productService);
        databaseCleanUp.truncateAllTables();
    }

    private UserEntity createUser(String userId) {
        return userService.signup(userId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), userId + "@test.com");
    }

    // ─────────────────────────────────────────────
    // addLike — 좋아요 등록
    // ─────────────────────────────────────────────

    @DisplayName("좋아요 등록")
    @Nested
    class AddLike {

        @DisplayName("[ECP] 좋아요 등록 시 상품의 likeCount가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenLikeIsAdded() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            // act
            likeFacade.addLike(user.getId(), product.id());

            // assert
            ProductInfo result = productFacade.getProduct(product.id());
            assertEquals(1L, result.likeCount());
        }

        @DisplayName("[State Transition] soft-deleted 좋아요를 재등록하면 restore되고 likeCount가 1 증가한다.")
        @Test
        void restoresLike_andIncrementsLikeCount_whenSoftDeletedLikeExists() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            likeService.like(user.getId(), product.id());
            likeService.unlike(user.getId(), product.id());

            // act
            likeFacade.addLike(user.getId(), product.id());

            // assert
            ProductInfo result = productFacade.getProduct(product.id());
            assertEquals(1L, result.likeCount());
        }

        @DisplayName("[ECP] 이미 좋아요한 상품을 재등록하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            likeFacade.addLike(user.getId(), product.id());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeFacade.addLike(user.getId(), product.id()));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // removeLike — 좋아요 취소
    // ─────────────────────────────────────────────

    @DisplayName("좋아요 취소")
    @Nested
    class RemoveLike {

        @DisplayName("[ECP] 좋아요 취소 시 상품의 likeCount가 1 감소하고 soft delete된다.")
        @Test
        void decrementsLikeCount_whenLikeIsRemoved() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            likeFacade.addLike(user.getId(), product.id());

            // act
            likeFacade.removeLike(user.getId(), product.id());

            // assert
            ProductInfo result = productFacade.getProduct(product.id());
            assertEquals(0L, result.likeCount());
        }

        @DisplayName("[ECP] 좋아요하지 않은 상품을 취소하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLikeNotExists() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeFacade.removeLike(user.getId(), product.id()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getLikedProducts — 좋아요한 상품 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("좋아요한 상품 목록 조회")
    @Nested
    class GetLikedProducts {

        @DisplayName("[ECP] 본인의 좋아요 목록 조회 시 좋아요한 상품 정보가 반환된다.")
        @Test
        void returnsLikedProducts_whenOwnerRequests() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            likeFacade.addLike(user.getId(), product.id());

            // act
            Page<LikeInfo> result = likeFacade.getLikedProducts(user.getId(), user.getId(), PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertEquals(1, result.getTotalElements()),
                    () -> assertEquals(product.id(), result.getContent().get(0).id()),
                    () -> assertEquals("나이키", result.getContent().get(0).brandName()),
                    () -> assertEquals("에어맥스", result.getContent().get(0).name())
            );
        }

        @DisplayName("[ECP] 타인의 좋아요 목록을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOtherUserRequests() {
            // arrange
            UserEntity owner = createUser("testuser1");
            UserEntity other = createUser("testuser2");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeFacade.getLikedProducts(other.getId(), owner.getId(), PageRequest.of(0, 20)));
            assertEquals(ErrorType.FORBIDDEN, exception.getErrorType());
        }

        @DisplayName("[ECP] 좋아요한 상품이 없으면 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoLikesExist() {
            // arrange
            UserEntity user = createUser("testuser1");

            // act
            Page<LikeInfo> result = likeFacade.getLikedProducts(user.getId(), user.getId(), PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // 트랜잭션 원자성
    // ─────────────────────────────────────────────

    @DisplayName("트랜잭션 원자성")
    @Nested
    class TransactionalAtomicity {

        @DisplayName("[Transactional] 좋아요 등록 중 likeCount 증가 실패 시 like row도 함께 롤백된다.")
        @Test
        void rollsBackLikeRow_whenLikeCountIncrementFails() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            doThrow(new RuntimeException("강제 실패")).when(productService).incrementLikeCount(product.id());

            // act
            assertThrows(RuntimeException.class, () -> likeFacade.addLike(user.getId(), product.id()));

            // assert: like row가 롤백되어 active like가 없음 → removeLike는 NOT_FOUND
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeFacade.removeLike(user.getId(), product.id()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());

            // assert: likeCount도 롤백되어 0 유지
            assertEquals(0L, productFacade.getProduct(product.id()).likeCount());
        }

        @DisplayName("[Transactional] 좋아요 취소 중 likeCount 감소 실패 시 like soft-delete도 함께 롤백된다.")
        @Test
        void rollsBackLikeSoftDelete_whenLikeCountDecrementFails() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            likeFacade.addLike(user.getId(), product.id());

            doThrow(new RuntimeException("강제 실패")).when(productService).decrementLikeCount(product.id());

            // act
            assertThrows(RuntimeException.class, () -> likeFacade.removeLike(user.getId(), product.id()));

            // assert: soft-delete가 롤백되어 like가 여전히 active → addLike는 CONFLICT
            CoreException exception = assertThrows(CoreException.class,
                    () -> likeFacade.addLike(user.getId(), product.id()));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());

            // assert: likeCount도 롤백되어 1 유지
            assertEquals(1L, productFacade.getProduct(product.id()).likeCount());
        }
    }
}
