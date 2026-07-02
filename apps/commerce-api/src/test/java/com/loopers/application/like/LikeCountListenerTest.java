package com.loopers.application.like;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LikeCountListenerTest {

    private final LikeCountRepository likeCountRepository = mock(LikeCountRepository.class);
    private final LikeCountListener listener = new LikeCountListener(likeCountRepository);

    @DisplayName("LikeAdded 를 받으면 likeCount 를 증가시킨다.")
    @Test
    void increments() {
        listener.onLikeAdded(new LikeAdded(7L, 100L, ZonedDateTime.now()));
        verify(likeCountRepository).increase(100L);
    }

    @DisplayName("LikeRemoved 를 받으면 likeCount 를 감소시킨다.")
    @Test
    void decrements() {
        listener.onLikeRemoved(new LikeRemoved(7L, 100L, ZonedDateTime.now()));
        verify(likeCountRepository).decrease(100L);
    }

    @DisplayName("집계 실패해도 예외를 밖으로 던지지 않는다. (좋아요 성공 보장)")
    @Test
    void swallowsException() {
        doThrow(new RuntimeException("db down")).when(likeCountRepository).increase(100L);
        assertDoesNotThrow(() -> listener.onLikeAdded(new LikeAdded(7L, 100L, ZonedDateTime.now())));
    }
}
