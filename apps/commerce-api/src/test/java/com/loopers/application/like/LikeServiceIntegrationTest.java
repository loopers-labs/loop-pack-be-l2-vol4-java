package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired private LikeService likeService;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        savedProduct = productJpaRepository.save(new ProductModel(brand, "나이키 에어맥스", 150_000));
        stockJpaRepository.save(new StockModel(savedProduct, 100));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private long currentLikeCount() {
        return productJpaRepository.findById(savedProduct.getId())
            .orElseThrow().getLikeCount();
    }

    @DisplayName("like()를 호출할 때,")
    @Nested
    class Like {

        @DisplayName("좋아요 등록 시 products.like_count가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenLikeRegistered() {
            // act
            likeService.like(1L, savedProduct.getId());

            // assert
            assertThat(currentLikeCount()).isEqualTo(1L);
        }

        @DisplayName("동일 사용자가 두 번 좋아요 시 like_count는 1이다 (멱등).")
        @Test
        void doesNotIncrementTwice_whenLikedAlready() {
            // act
            likeService.like(1L, savedProduct.getId());
            likeService.like(1L, savedProduct.getId()); // 중복 요청

            // assert
            assertThat(currentLikeCount()).isEqualTo(1L);
        }

        @DisplayName("서로 다른 두 사용자가 좋아요 시 like_count는 2이다.")
        @Test
        void incrementsTwice_whenTwoDifferentUsersLike() {
            // act
            likeService.like(1L, savedProduct.getId());
            likeService.like(2L, savedProduct.getId());

            // assert
            assertThat(currentLikeCount()).isEqualTo(2L);
        }
    }

    @DisplayName("unlike()를 호출할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요 취소 시 products.like_count가 1 감소한다.")
        @Test
        void decrementsLikeCount_whenUnliked() {
            // arrange
            likeService.like(1L, savedProduct.getId());
            assertThat(currentLikeCount()).isEqualTo(1L);

            // act
            likeService.unlike(1L, savedProduct.getId());

            // assert
            assertThat(currentLikeCount()).isEqualTo(0L);
        }

        @DisplayName("좋아요가 없는 상태에서 취소 시 like_count는 0에서 감소하지 않는다 (멱등, 음수 방지).")
        @Test
        void doesNotDecrementBelowZero_whenUnlikedWithNoLike() {
            // act
            likeService.unlike(1L, savedProduct.getId()); // 좋아요 없는 상태

            // assert
            assertThat(currentLikeCount()).isEqualTo(0L);
        }
    }
}
