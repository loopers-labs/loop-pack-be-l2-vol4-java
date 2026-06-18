package com.loopers.like.application;

import com.loopers.like.domain.LikeService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeFacadeIntegrationTest {

    private final LikeFacade likeFacade;
    private final LikeService likeService;
    private final ProductService productService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    LikeFacadeIntegrationTest(
        LikeFacade likeFacade,
        LikeService likeService,
        ProductService productService,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.likeService = likeService;
        this.productService = productService;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때 ")
    @Nested
    class LikeProduct {

        @DisplayName("새 좋아요가 생성되면, 좋아요 수 증가 변경 기록을 저장한다.")
        @Test
        void savesIncreaseChange_whenLikeIsCreated() {
            // arrange
            Long userId = 1L;
            Product product = createProduct();

            // act
            likeFacade.like(userId, product.getId());

            // assert
            assertThat(changeAmounts(product.getId())).containsExactly(1);
        }

        @DisplayName("이미 좋아요한 상품에 다시 등록하면, 변경 기록을 추가하지 않는다.")
        @Test
        void doesNotSaveChange_whenProductIsAlreadyLiked() {
            // arrange
            Long userId = 1L;
            Product product = createProduct();
            likeFacade.like(userId, product.getId());

            // act
            likeFacade.like(userId, product.getId());

            // assert
            assertThat(changeAmounts(product.getId())).containsExactly(1);
        }
    }

    @DisplayName("좋아요를 취소할 때 ")
    @Nested
    class UnlikeProduct {

        @DisplayName("기존 좋아요가 삭제되면, 좋아요 수 감소 변경 기록을 저장한다.")
        @Test
        void savesDecreaseChange_whenLikeIsDeleted() {
            // arrange
            Long userId = 1L;
            Product product = createProduct();
            likeService.like(userId, product.getId());

            // act
            likeFacade.unlike(userId, product.getId());

            // assert
            assertThat(changeAmounts(product.getId())).containsExactly(-1);
        }

        @DisplayName("기존 좋아요가 없으면, 변경 기록을 추가하지 않는다.")
        @Test
        void doesNotSaveChange_whenLikeDoesNotExist() {
            // arrange
            Long userId = 1L;
            Product product = createProduct();

            // act
            likeFacade.unlike(userId, product.getId());

            // assert
            assertThat(changeAmounts(product.getId())).isEmpty();
        }
    }

    private Product createProduct() {
        return productService.createProduct(
            1L,
            "아이폰 16 Pro",
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            1_550_000L
        );
    }

    private List<Integer> changeAmounts(Long productId) {
        return jdbcTemplate.queryForList(
            """
                select change_amount
                from product_like_count_change
                where product_id = ?
                order by id
                """,
            Integer.class,
            productId
        );
    }
}
