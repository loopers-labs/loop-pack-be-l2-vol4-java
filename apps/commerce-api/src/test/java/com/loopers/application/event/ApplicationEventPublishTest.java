package com.loopers.application.event;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.like.LikeChangedEvent;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class ApplicationEventPublishTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("신규 좋아요 등록 시, LikeChangedEvent(LIKED)가 1건 발행된다.")
    @Test
    void publishesLikedEvent() {
        // when
        likeService.like(1L, 100L);

        // then
        List<LikeChangedEvent> published = events.stream(LikeChangedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).action()).isEqualTo(LikeChangedEvent.Action.LIKED);
    }

    @DisplayName("이미 좋아요 상태에서 다시 등록하면, 추가 이벤트가 발행되지 않는다.")
    @Test
    void doesNotRepublish_whenAlreadyLiked() {
        // when
        likeService.like(1L, 100L);
        likeService.like(1L, 100L);

        // then
        assertThat(events.stream(LikeChangedEvent.class).count()).isEqualTo(1);
    }

    @DisplayName("좋아요 취소 시, LikeChangedEvent(UNLIKED)가 발행된다.")
    @Test
    void publishesUnlikedEvent() {
        // given
        likeService.like(1L, 100L);

        // when
        likeService.unlike(1L, 100L);

        // then
        long unliked = events.stream(LikeChangedEvent.class)
                .filter(event -> event.action() == LikeChangedEvent.Action.UNLIKED)
                .count();
        assertThat(unliked).isEqualTo(1);
    }

    @DisplayName("상품 상세 조회 기록 시, ProductViewedEvent가 발행된다.")
    @Test
    void publishesViewedEvent() {
        // when
        productFacade.recordView(100L, null);

        // then
        assertThat(events.stream(ProductViewedEvent.class).count()).isEqualTo(1);
    }
}