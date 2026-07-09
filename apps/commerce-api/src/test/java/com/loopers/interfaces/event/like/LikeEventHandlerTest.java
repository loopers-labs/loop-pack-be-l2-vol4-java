package com.loopers.interfaces.event.like;

import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.infrastructure.product.ProductLikeViewJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeEventHandlerTest {

    @Autowired
    private LikeEventHandler likeEventHandler;

    @Autowired
    private ProductLikeViewJpaRepository productLikeViewJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 이벤트를 처리할 때,")
    @Nested
    class Handle {

        @DisplayName("LikedEvent가 발생하면, 좋아요 수가 1 증가한다.")
        @Test
        void handle_incrementsLikeCount_whenLiked() {
            // arrange
            Long productId = 1L;
            productLikeViewJpaRepository.save(new ProductLikeViewModel(productId));
            LikedEvent event = new LikedEvent(1L, productId);

            // act
            AopTestUtils.<LikeEventHandler>getTargetObject(likeEventHandler).handle(event);

            // assert
            ProductLikeViewModel view = productLikeViewJpaRepository.findById(productId).orElseThrow();
            assertThat(view.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("UnlikedEvent가 발생하면, 좋아요 수가 1 감소한다.")
        @Test
        void handle_decrementsLikeCount_whenUnliked() {
            // arrange
            Long productId = 1L;
            ProductLikeViewModel view = new ProductLikeViewModel(productId);
            view.increment();
            productLikeViewJpaRepository.save(view);
            UnlikedEvent event = new UnlikedEvent(1L, productId);

            // act
            AopTestUtils.<LikeEventHandler>getTargetObject(likeEventHandler).handle(event);

            // assert
            ProductLikeViewModel updated = productLikeViewJpaRepository.findById(productId).orElseThrow();
            assertThat(updated.getLikeCount()).isEqualTo(0);
        }
    }
}
