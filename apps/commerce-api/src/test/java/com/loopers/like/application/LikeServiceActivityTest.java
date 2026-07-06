package com.loopers.like.application;

import com.loopers.activity.UserAction;
import com.loopers.activity.UserActivityEvent;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.application.ProductReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeServiceActivityTest {

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LikeService likeService = new LikeService(likeRepository, productReader, eventPublisher);

    @Test
    @DisplayName("좋아요 등록 시 유저 행동 이벤트(LIKE)를 발행한다")
    void givenNewLike_whenRegister_thenPublishesUserActivityEvent() {
        when(likeRepository.findByUserIdAndProductId(1L, 100L)).thenReturn(Optional.empty());

        likeService.register(1L, 100L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        UserActivityEvent activity = captor.getAllValues().stream()
                .filter(UserActivityEvent.class::isInstance)
                .map(UserActivityEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(activity.userId()).isEqualTo(1L);
        assertThat(activity.action()).isEqualTo(UserAction.LIKE);
        assertThat(activity.targetId()).isEqualTo(100L);
    }
}
