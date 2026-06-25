package com.loopers.product.application;

import com.loopers.like.application.LikeFacade;
import com.loopers.like.domain.LikeChange;
import com.loopers.like.domain.LikeService;
import com.loopers.like.domain.ProductLikeCountChange;
import com.loopers.like.domain.ProductLikeCountChangeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "commerce.product-like-summary.sync-chunk-size=2",
    "commerce.product-like-summary.sync-max-chunks-per-run=3"
})
class ProductLikeSummarySynchronizerIntegrationTest {

    private final ProductLikeSummarySynchronizer synchronizer;
    private final LikeFacade likeFacade;
    private final LikeService likeService;
    private final ProductLikeCountChangeRepository productLikeCountChangeRepository;
    private final ProductService productService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductLikeSummarySynchronizerIntegrationTest(
        ProductLikeSummarySynchronizer synchronizer,
        LikeFacade likeFacade,
        LikeService likeService,
        ProductLikeCountChangeRepository productLikeCountChangeRepository,
        ProductService productService,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.synchronizer = synchronizer;
        this.likeFacade = likeFacade;
        this.likeService = likeService;
        this.productLikeCountChangeRepository = productLikeCountChangeRepository;
        this.productService = productService;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품별 좋아요 수 변경분을 요약 테이블에 합산 반영한다.")
    @Test
    void appliesProductLikeCountChangesToSummary() {
        // arrange
        Product iphone = createProduct(1L);
        Product macbook = createProduct(1L);
        createSummary(iphone, 10);
        createSummary(macbook, 3);

        likeFacade.like(1L, iphone.getId());
        likeFacade.like(2L, iphone.getId());
        likeService.like(3L, macbook.getId());
        likeFacade.unlike(3L, macbook.getId());
        long maxChangeId = maxChangeId();

        // act
        synchronizer.sync();

        // assert
        assertAll(
            () -> assertThat(likeCount(iphone.getId())).isEqualTo(12),
            () -> assertThat(likeCount(macbook.getId())).isEqualTo(2),
            () -> assertThat(lastCheckpointChangeId()).isEqualTo(maxChangeId)
        );
    }

    @DisplayName("이미 반영한 변경분은 다시 반영하지 않는다.")
    @Test
    void doesNotApplyProcessedChangesAgain() {
        // arrange
        Product product = createProduct(1L);
        createSummary(product, 0);
        likeFacade.like(1L, product.getId());

        // act
        synchronizer.sync();
        synchronizer.sync();

        // assert
        assertThat(likeCount(product.getId())).isEqualTo(1);
    }

    @DisplayName("반영할 변경분이 없으면 체크포인트를 0으로 유지한다.")
    @Test
    void keepsCheckpointWhenNoChangesExist() {
        // act
        synchronizer.sync();

        // assert
        assertThat(lastCheckpointChangeId()).isZero();
    }

    @DisplayName("한 번의 실행에서 설정된 최대 chunk 수까지만 변경분을 반영한다.")
    @Test
    void appliesChangesUpToMaxChunksPerRun() {
        // arrange
        Product product = createProduct(1L);
        createSummary(product, 0);
        saveIncreaseChanges(product, 7);
        long sixthChangeId = changeIdAtOffset(5);
        long maxChangeId = maxChangeId();

        // act
        synchronizer.sync();

        // assert
        assertAll(
            () -> assertThat(likeCount(product.getId())).isEqualTo(6),
            () -> assertThat(lastCheckpointChangeId()).isEqualTo(sixthChangeId)
        );

        // act
        synchronizer.sync();

        // assert
        assertAll(
            () -> assertThat(likeCount(product.getId())).isEqualTo(7),
            () -> assertThat(lastCheckpointChangeId()).isEqualTo(maxChangeId)
        );
    }

    private Product createProduct(Long brandId) {
        return productService.createProduct(
            brandId,
            "아이폰 16 Pro",
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            1_550_000L
        );
    }

    private void createSummary(Product product, long likeCount) {
        jdbcTemplate.update(
            """
                insert into product_like_summary(product_id, brand_id, like_count)
                values (?, ?, ?)
                """,
            product.getId(),
            product.getBrandId(),
            likeCount
        );
    }

    private void saveIncreaseChanges(Product product, int count) {
        for (int i = 0; i < count; i++) {
            productLikeCountChangeRepository.save(ProductLikeCountChange.from(LikeChange.increased(product.getId())));
        }
    }

    private long likeCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select like_count from product_like_summary where product_id = ?",
            Long.class,
            productId
        );
    }

    private long changeIdAtOffset(int offset) {
        return jdbcTemplate.queryForObject(
            """
                select id
                from product_like_count_change
                order by id
                limit 1 offset ?
                """,
            Long.class,
            offset
        );
    }

    private long maxChangeId() {
        return jdbcTemplate.queryForObject(
            "select coalesce(max(id), 0) from product_like_count_change",
            Long.class
        );
    }

    private long lastCheckpointChangeId() {
        return jdbcTemplate.queryForObject(
            """
                select last_change_id
                from product_like_summary_checkpoint
                where checkpoint_name = 'summary'
                """,
            Long.class
        );
    }
}
