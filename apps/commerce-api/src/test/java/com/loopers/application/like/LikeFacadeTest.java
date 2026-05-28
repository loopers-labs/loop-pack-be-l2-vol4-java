package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeFacadeTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        ProductModel product = productRepository.save(
                new ProductModel(brand.getId(), "운동화", null, Money.of(1000L), Quantity.of(10), null));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록하면 like 행이 생기고 likeCount 가 1 증가한다.")
    @Test
    void like_createsLikeAndIncreasesCount() {
        likeFacade.like(USER_ID, productId);

        assertThat(likeRepository.existsByUserIdAndProductId(USER_ID, productId)).isTrue();
        assertThat(productRepository.findById(productId).get().getLikeCount()).isEqualTo(1);
    }

    @DisplayName("같은 사용자가 좋아요를 두 번 등록해도 likeCount 는 1 이다. (멱등)")
    @Test
    void like_isIdempotent() {
        likeFacade.like(USER_ID, productId);
        likeFacade.like(USER_ID, productId);

        assertThat(productRepository.findById(productId).get().getLikeCount()).isEqualTo(1);
    }

    @DisplayName("좋아요를 취소하면 like 행이 사라지고 likeCount 가 1 감소한다.")
    @Test
    void unlike_removesLikeAndDecreasesCount() {
        likeFacade.like(USER_ID, productId);
        likeFacade.unlike(USER_ID, productId);

        assertThat(likeRepository.existsByUserIdAndProductId(USER_ID, productId)).isFalse();
        assertThat(productRepository.findById(productId).get().getLikeCount()).isEqualTo(0);
    }

    @DisplayName("좋아요하지 않은 상태에서 취소해도 에러 없이 likeCount 는 0 이다. (멱등)")
    @Test
    void unlike_isIdempotent_whenNotLiked() {
        likeFacade.unlike(USER_ID, productId);

        assertThat(productRepository.findById(productId).get().getLikeCount()).isEqualTo(0);
    }

    @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND.")
    @Test
    void like_throwsNotFound_whenProductDoesNotExist() {
        CoreException result = assertThrows(CoreException.class,
                () -> likeFacade.like(USER_ID, 999_999L));
        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }
}
