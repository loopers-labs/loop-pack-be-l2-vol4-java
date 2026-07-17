package com.loopers.like.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.like.domain.LikeRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LikeServiceTest {

  private final LikeRepository likeRepository = mock(LikeRepository.class);
  private final LikeService likeService = new LikeService(likeRepository);

  @DisplayName("저장소에 실제로 신규 저장된 경우에만 true를 반환한다.")
  @Test
  void returnsWhetherLikeWasInserted() {
    when(likeRepository.saveIfAbsent(1L, 10L)).thenReturn(true, false);

    boolean first = likeService.like(1L, 10L);
    boolean duplicate = likeService.like(1L, 10L);

    assertThat(first).isTrue();
    assertThat(duplicate).isFalse();
  }

  @DisplayName("여러 상품의 좋아요 수를 한 번에 조회하고 미집계 상품은 0으로 채운다.")
  @Test
  void getsLikeCountsInBatch() {
    when(likeRepository.countByProductIds(Set.of(10L, 20L, 30L)))
        .thenReturn(Map.of(10L, 2L, 30L, 1L));

    Map<Long, Long> result = likeService.getLikeCounts(List.of(10L, 20L, 10L, 30L));

    assertThat(result).containsAllEntriesOf(Map.of(10L, 2L, 20L, 0L, 30L, 1L)).hasSize(3);
    verify(likeRepository).countByProductIds(Set.of(10L, 20L, 30L));
  }
}
