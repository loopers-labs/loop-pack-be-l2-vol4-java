package com.loopers.application.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword("Kyle!2030")
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private ProductModel saveProduct() {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(1L)
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build());
    }

    private int likeCountOf(Long productId) {
        return productJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    @DisplayName("좋아요를 등록하면,")
    @Nested
    class CreateLike {

        @DisplayName("좋아요 수가 실제 좋아요 행 수와 일치하도록 증가한다.")
        @Test
        void incrementsLikeCount_toMatchActualLikes() {
            // arrange
            UserModel user1 = saveUser("testuser1");
            UserModel user2 = saveUser("testuser2");
            ProductModel product = saveProduct();

            // act
            likeFacade.createLike(user1.getId(), product.getId());
            likeFacade.createLike(user2.getId(), product.getId());

            // assert
            assertAll(
                () -> assertThat(likeCountOf(product.getId())).isEqualTo(2),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(2L)
            );
        }

        @DisplayName("같은 회원이 다시 등록해도 좋아요 수가 중복 증가하지 않는다(멱등).")
        @Test
        void doesNotDoubleCount_whenSameUserLikesAgain() {
            // arrange
            UserModel user = saveUser("testuser1");
            ProductModel product = saveProduct();

            // act
            likeFacade.createLike(user.getId(), product.getId());
            likeFacade.createLike(user.getId(), product.getId());

            // assert
            assertAll(
                () -> assertThat(likeCountOf(product.getId())).isEqualTo(1),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("좋아요를 취소하면,")
    @Nested
    class DeleteLike {

        @DisplayName("좋아요 수가 실제 좋아요 행 수와 일치하도록 감소한다.")
        @Test
        void decrementsLikeCount_toMatchActualLikes() {
            // arrange
            UserModel user1 = saveUser("testuser1");
            UserModel user2 = saveUser("testuser2");
            ProductModel product = saveProduct();
            likeFacade.createLike(user1.getId(), product.getId());
            likeFacade.createLike(user2.getId(), product.getId());

            // act
            likeFacade.deleteLike(user1.getId(), product.getId());

            // assert
            assertAll(
                () -> assertThat(likeCountOf(product.getId())).isEqualTo(1),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1L)
            );
        }

        @DisplayName("좋아요 기록이 없는 상품을 취소해도 좋아요 수가 음수가 되지 않는다(멱등).")
        @Test
        void keepsZero_whenNoLikeToCancel() {
            // arrange
            UserModel user = saveUser("testuser1");
            ProductModel product = saveProduct();

            // act
            likeFacade.deleteLike(user.getId(), product.getId());

            // assert
            assertAll(
                () -> assertThat(likeCountOf(product.getId())).isEqualTo(0),
                () -> assertThat(likeJpaRepository.count()).isEqualTo(0L)
            );
        }
    }
}
