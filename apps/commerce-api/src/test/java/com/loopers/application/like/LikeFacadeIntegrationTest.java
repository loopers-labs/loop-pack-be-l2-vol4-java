package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeFacadeIntegrationTest {

    private static final String LOGIN_ID = "testuser01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new UserModel(
            LOGIN_ID, LOGIN_PW, "테스트유저", "1990-01-01", "test@example.com", Gender.MALE, passwordEncryptor
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel createBrand() {
        return brandRepository.save(new BrandModel("테스트브랜드"));
    }

    private Long createProduct(Long brandId) {
        return productRepository.save(new ProductModel(brandId, "상품", BigDecimal.valueOf(1000))).getId();
    }

    private Long createProduct() {
        return createProduct(1L);
    }

    private Long likeCountOf(Long productId) {
        return productRepository.find(productId).orElseThrow().getLikeCount();
    }

    @DisplayName("좋아요 상품 목록을 조회할 때,")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요한 상품이 있으면 해당 상품 정보 목록을 반환한다.")
        @Test
        void returnsProductInfoList_whenLikesExist() {
            // given
            BrandModel brand = createBrand();
            Long productId = createProduct(brand.getId());
            likeFacade.like(LOGIN_ID, LOGIN_PW, productId);

            // when
            List<ProductInfo> result = likeFacade.getLikedProducts(LOGIN_ID, LOGIN_PW, savedUser.getId());

            // then
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).id()).isEqualTo(productId),
                () -> assertThat(result.get(0).brandName()).isEqualTo(brand.getName())
            );
        }

        @DisplayName("좋아요한 상품이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoLikesExist() {
            // when
            List<ProductInfo> result = likeFacade.getLikedProducts(LOGIN_ID, LOGIN_PW, savedUser.getId());

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("다른 사용자의 좋아요 목록을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenUserIdDoesNotMatch() {
            // given
            Long anotherUserId = savedUser.getId() + 1;

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> likeFacade.getLikedProducts(LOGIN_ID, LOGIN_PW, anotherUserId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("좋아요를 누를 때,")
    @Nested
    class Like {

        @DisplayName("새 좋아요면 상품의 좋아요 수가 1 증가한다.")
        @Test
        void increasesLikeCount_whenNewLike() {
            // given
            Long productId = createProduct();

            // when
            likeFacade.like(LOGIN_ID, LOGIN_PW, productId);

            // then
            assertThat(likeCountOf(productId)).isEqualTo(1L);
        }

        @DisplayName("이미 좋아요한 상태면 멱등하게 처리되어 좋아요 수가 중복 증가하지 않는다.")
        @Test
        void doesNotDoubleCount_whenAlreadyLiked() {
            // given
            Long productId = createProduct();
            likeFacade.like(LOGIN_ID, LOGIN_PW, productId);

            // when
            likeFacade.like(LOGIN_ID, LOGIN_PW, productId);

            // then
            assertThat(likeCountOf(productId)).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND 예외가 발생하고 좋아요가 저장되지 않는다.")
        @Test
        void throwsNotFoundAndRollsBack_whenProductDoesNotExist() {
            // given
            Long nonExistentProductId = 999L;

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> likeFacade.like(LOGIN_ID, LOGIN_PW, nonExistentProductId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(likeRepository.findByUserIdAndProductId(savedUser.getId(), nonExistentProductId)).isEmpty();
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 있으면 취소되어 상품의 좋아요 수가 1 감소한다.")
        @Test
        void decreasesLikeCount_whenLikeExists() {
            // given
            Long productId = createProduct();
            likeFacade.like(LOGIN_ID, LOGIN_PW, productId);

            // when
            likeFacade.unlike(LOGIN_ID, LOGIN_PW, productId);

            // then
            assertThat(likeCountOf(productId)).isEqualTo(0L);
        }

        @DisplayName("좋아요가 없으면 멱등하게 처리되어 좋아요 수가 감소하지 않는다.")
        @Test
        void doesNotUnderCount_whenLikeDoesNotExist() {
            // given
            Long productId = createProduct();

            // when
            likeFacade.unlike(LOGIN_ID, LOGIN_PW, productId);

            // then
            assertThat(likeCountOf(productId)).isEqualTo(0L);
        }
    }
}
