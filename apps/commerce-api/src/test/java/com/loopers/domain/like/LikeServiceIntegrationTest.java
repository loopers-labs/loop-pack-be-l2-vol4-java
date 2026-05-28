package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Transactional
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
        ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {

        @DisplayName("처음 좋아요 시, LikeModel이 저장된다.")
        @Test
        void savesLike_whenFirstLike() {
            LikeModel like = likeService.like(userId, productId);

            assertAll(
                () -> assertThat(like.getId()).isNotNull(),
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("이미 좋아요한 경우, 기존 LikeModel을 반환한다. (멱등)")
        @Test
        void returnsExisting_whenAlreadyLiked() {
            LikeModel first = likeService.like(userId, productId);
            LikeModel second = likeService.like(userId, productId);

            assertThat(second.getId()).isEqualTo(first.getId());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 있으면, 삭제 후 true를 반환한다.")
        @Test
        void returnsTrue_whenLikeExists() {
            likeService.like(userId, productId);

            boolean deleted = likeService.unlike(userId, productId);

            assertAll(
                () -> assertThat(deleted).isTrue(),
                () -> assertThat(likeService.find(userId, productId)).isEmpty()
            );
        }

        @DisplayName("좋아요가 없으면, false를 반환한다. (멱등)")
        @Test
        void returnsFalse_whenLikeNotExists() {
            boolean deleted = likeService.unlike(userId, productId);

            assertThat(deleted).isFalse();
        }
    }

    @DisplayName("좋아요 목록을 조회할 때,")
    @Nested
    class FindAllByUserId {

        @DisplayName("유저의 좋아요 목록을 페이징으로 반환한다.")
        @Test
        void returnsPagedList_whenLikesExist() {
            BrandModel brand = brandService.create("나이키2", "설명2");
            ProductModel product2 = productService.create(brand, "상품2", "설명2", 20_000L);

            likeService.like(userId, productId);
            likeService.like(userId, product2.getId());

            Page<LikeModel> page = likeService.findAllByUserId(userId, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }
    }
}
