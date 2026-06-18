package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class LikeApplicationServiceTest {

    @Autowired private LikeApplicationService likeApplicationService;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandEntity savedBrand() {
        return brandJpaRepository.save(BrandEntity.from(new BrandModel("나이키", "스포츠 브랜드")));
    }

    private ProductEntity savedProduct(BrandEntity brand) {
        return productJpaRepository.save(ProductEntity.from(
            new ProductModel(brand.getId(), "에어맥스", "운동화", 100_000L, 10),
            brand
        ));
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class Like {

        @DisplayName("like_count가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenLiked() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);

            likeApplicationService.like(1L, product.getId());

            ProductEntity updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.toDomain().getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("여러 명이 좋아요하면 like_count가 정확히 반영된다.")
        @Test
        void incrementsLikeCount_forEachUser() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);

            likeApplicationService.like(1L, product.getId());
            likeApplicationService.like(2L, product.getId());
            likeApplicationService.like(3L, product.getId());

            ProductEntity updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.toDomain().getLikeCount()).isEqualTo(3L);
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class Unlike {

        @DisplayName("like_count가 1 감소한다.")
        @Test
        void decrementsLikeCount_whenUnliked() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);

            likeApplicationService.like(1L, product.getId());
            likeApplicationService.unlike(1L, product.getId());

            ProductEntity updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.toDomain().getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("like_count는 0 미만으로 내려가지 않는다.")
        @Test
        void likeCountDoesNotGoBelowZero() {
            BrandEntity brand = savedBrand();
            ProductEntity product = savedProduct(brand);

            likeApplicationService.like(1L, product.getId());
            likeApplicationService.unlike(1L, product.getId());

            // like_count가 이미 0인 상태에서 decrementLikeCount 직접 호출
            productJpaRepository.decrementLikeCount(product.getId());

            ProductEntity updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.toDomain().getLikeCount()).isEqualTo(0L);
        }
    }
}
