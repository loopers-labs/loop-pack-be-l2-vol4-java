package com.loopers.tddstudy.infrastructure.ranking;

import com.loopers.tddstudy.domain.ranking.RankingItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PeriodRankingMvRepository.class)
class PeriodRankingMvRepositoryTest {

    @Autowired PeriodRankingMvRepository repository;
    @Autowired ProductRankWeeklyJpaRepository weeklyJpa;
    @Autowired ProductRankMonthlyJpaRepository monthlyJpa;

    @Test
    void 주간_랭킹을_등수순으로_가져온다() {
        weeklyJpa.save(new ProductRankWeekly("2026-W27", 2, 202L, 5.0, 0, 0, 0));
        weeklyJpa.save(new ProductRankWeekly("2026-W27", 1, 101L, 9.0, 0, 0, 0));

        List<RankingItem> items = repository.getWeeklyPage("2026-W27", 1, 20);

        assertThat(items).containsExactly(
                new RankingItem(101L, 9.0),
                new RankingItem(202L, 5.0)
        );
    }

    @Test
    void 페이지_번호는_1부터_시작한다() {
        for (int i = 1; i <= 5; i++) {
            weeklyJpa.save(new ProductRankWeekly("2026-W27", i, (long) i, 10.0 - i, 0, 0, 0));
        }

        List<RankingItem> page2 = repository.getWeeklyPage("2026-W27", 2, 2);   // 3등, 4등

        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).productId()).isEqualTo(3L);
    }

    @Test
    void 없는_기간을_조회하면_빈_목록이다() {
        assertThat(repository.getWeeklyPage("2099-W01", 1, 20)).isEmpty();
    }

    @Test
    void 월간_랭킹도_같은_방식으로_가져온다() {
        monthlyJpa.save(new ProductRankMonthly("2026-07", 1, 101L, 9.0, 0, 0, 0));

        List<RankingItem> items = repository.getMonthlyPage("2026-07", 1, 20);

        assertThat(items).containsExactly(new RankingItem(101L, 9.0));
    }
}
