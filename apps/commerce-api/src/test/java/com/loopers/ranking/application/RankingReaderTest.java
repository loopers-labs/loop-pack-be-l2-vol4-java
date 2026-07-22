package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.RecoverableDataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingReaderTest {

    private final RankingRepository rankingRepository = mock(RankingRepository.class);
    private final RankingReader rankingReader = new RankingReader(rankingRepository);

    @DisplayName("0-based 순위에 +1 하여 1-based 로 돌려준다")
    @Test
    void todayRank_isOneBased() {
        when(rankingRepository.rank(any(), anyLong())).thenReturn(0L);
        assertThat(rankingReader.todayRank(101L)).isEqualTo(1);
    }

    @DisplayName("랭킹에 없으면 null 이다")
    @Test
    void todayRank_isNull_whenAbsent() {
        when(rankingRepository.rank(any(), anyLong())).thenReturn(null);
        assertThat(rankingReader.todayRank(999L)).isNull();
    }

    @DisplayName("Redis 장애 시 null 을 돌려 상세 조회를 막지 않는다")
    @Test
    void todayRank_isNull_whenRedisDown() {
        when(rankingRepository.rank(any(), anyLong())).thenThrow(new RecoverableDataAccessException("redis down"));
        assertThat(rankingReader.todayRank(101L)).isNull();
    }
}
