package com.loopers.tddstudy.infrastructure.ranking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductRankMvJpaRepositoryTest {

    @Autowired ProductRankWeeklyJpaRepository weeklyRepository;
    @Autowired ProductRankMonthlyJpaRepository monthlyRepository;

    @Test
    void 특정_기간의_랭킹만_삭제한다() {
        weeklyRepository.save(new ProductRankWeekly("2026-W27", 1, 1L, 9.0, 10, 10, 0));
        weeklyRepository.save(new ProductRankWeekly("2026-W27", 2, 2L, 3.5, 0, 5, 0));
        weeklyRepository.save(new ProductRankWeekly("2026-W28", 1, 3L, 7.0, 0, 10, 0));

        weeklyRepository.deleteByPeriodKey("2026-W27");

        assertThat(weeklyRepository.count()).isEqualTo(1);   // W28만 남음
    }

    @Test
    void 등수_오름차순으로_조회한다() {
        // 일부러 뒤죽박죽 저장
        weeklyRepository.save(new ProductRankWeekly("2026-W27", 3, 3L, 1.0, 0, 0, 0));
        weeklyRepository.save(new ProductRankWeekly("2026-W27", 1, 1L, 9.0, 0, 0, 0));
        weeklyRepository.save(new ProductRankWeekly("2026-W27", 2, 2L, 5.0, 0, 0, 0));

        List<ProductRankWeekly> result =
                weeklyRepository.findByPeriodKeyOrderByRankNoAsc("2026-W27", PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getRankNo()).isEqualTo(1);   // 1등이 맨 앞
        assertThat(result.get(1).getRankNo()).isEqualTo(2);
        assertThat(result.get(2).getRankNo()).isEqualTo(3);
    }

    @Test
    void 페이지_단위로_잘라서_조회한다() {
        for (int i = 1; i <= 5; i++) {
            weeklyRepository.save(new ProductRankWeekly("2026-W27", i, (long) i, 10.0 - i, 0, 0, 0));
        }

        // 2페이지, 페이지당 2개 → 3등, 4등
        List<ProductRankWeekly> page2 =
                weeklyRepository.findByPeriodKeyOrderByRankNoAsc("2026-W27", PageRequest.of(1, 2));

        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).getRankNo()).isEqualTo(3);
        assertThat(page2.get(1).getRankNo()).isEqualTo(4);
    }

    @Test
    void 월간_랭킹도_같은_방식으로_동작한다() {
        monthlyRepository.save(new ProductRankMonthly("2026-07", 1, 1L, 9.0, 0, 0, 0));
        monthlyRepository.save(new ProductRankMonthly("2026-06", 1, 2L, 5.0, 0, 0, 0));

        List<ProductRankMonthly> result =
                monthlyRepository.findByPeriodKeyOrderByRankNoAsc("2026-07", PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo(1L);
    }
}
