package com.loopers.application.like;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.like.RedisLikeCountStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * LikeFacade 통합 — Like 관계 저장 + 좋아요 수 증감분(Redis) 흡수 검증.
 * <p>like row 저장은 RDB(원천), 좋아요 수 증감은 Redis 증감분으로 누적된다(배치가 product.like_count에 반영).
 * 그래서 여기서는 product.like_count 컬럼이 아니라 Redis 증감분을 검증한다. 멱등성·존재검증·취소 흐름까지 본다.</p>
 */
@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    // 증감분은 쓰기→마스터로 가므로, read-your-writes 보장을 위해 마스터 템플릿으로 읽는다(복제 지연 회피).
    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 49_000L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String delta() {
        return masterRedisTemplate.opsForValue().get(RedisLikeCountStore.DELTA_KEY_PREFIX + productId);
    }

    @DisplayName("좋아요 등록 합성 시")
    @Nested
    class Like {

        @DisplayName("새 좋아요면 product_like 행이 추가되고 좋아요 증감분이 1 누적된다")
        @Test
        void persistsLikeAndAccumulatesDelta() {
            // when
            likeFacade.like(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isTrue(),
                () -> assertThat(delta()).isEqualTo("1")
            );
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록해도 멱등으로 증감분은 1로 유지된다")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            // given
            likeFacade.like(userId, productId);

            // when
            likeFacade.like(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isEqualTo(1),
                () -> assertThat(delta()).isEqualTo("1")
            );
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND가 발생하고 like row도 증감분도 변하지 않는다")
        @Test
        void throwsNotFound_andDoesNothing_whenProductMissing() {
            // when / then
            assertThatThrownBy(() -> likeFacade.like(userId, 999_999L))
                .isInstanceOfSatisfying(CoreException.class, ex ->
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
            assertAll(
                () -> assertThat(likeJpaRepository.count()).isZero(),
                () -> assertThat(masterRedisTemplate.opsForValue().get(RedisLikeCountStore.DELTA_KEY_PREFIX + 999_999L)).isNull()
            );
        }
    }

    @DisplayName("좋아요 취소 합성 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 row가 사라지고 증감분이 1 감소한다")
        @Test
        void deletesLikeAndDecrementsDelta() {
            // given
            likeFacade.like(userId, productId);

            // when
            likeFacade.unlike(userId, productId);

            // then
            assertAll(
                () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isFalse(),
                () -> assertThat(delta()).isEqualTo("0")
            );
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 (상품은 존재) 멱등으로 증감분은 생성되지 않는다")
        @Test
        void isIdempotent_whenNothingToUnlike_andProductExists() {
            // when
            likeFacade.unlike(userId, productId);

            // then
            assertThat(delta()).isNull();
        }

        @DisplayName("존재하지 않는 상품을 unlike하면 NOT_FOUND가 발생한다 (like row 0건 + requireExists)")
        @Test
        void throwsNotFound_whenProductMissing() {
            // when / then
            assertThatThrownBy(() -> likeFacade.unlike(userId, 999_999L))
                .isInstanceOfSatisfying(CoreException.class, ex ->
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
