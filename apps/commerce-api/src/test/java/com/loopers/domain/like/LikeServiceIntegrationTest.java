package com.loopers.domain.like;

import com.loopers.application.like.LikeService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductLikeViewJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeViewJpaRepository productLikeViewJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("Like가 DB에 저장된다.")
        @Test
        void like_savesLike_atDbLevel() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            Long memberId = 1L;

            // act
            likeService.like(memberId, product.getId());

            // assert
            assertThat(likeRepository.findByMemberIdAndProductId(memberId, product.getId())).isPresent();
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("Like가 DB에서 삭제된다.")
        @Test
        void unlike_deletesLike_atDbLevel() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, 1L));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            Long memberId = 1L;
            likeService.like(memberId, product.getId());

            // act
            likeService.unlike(memberId, product.getId());

            // assert
            assertThat(likeRepository.findByMemberIdAndProductId(memberId, product.getId())).isEmpty();
        }
    }
}
