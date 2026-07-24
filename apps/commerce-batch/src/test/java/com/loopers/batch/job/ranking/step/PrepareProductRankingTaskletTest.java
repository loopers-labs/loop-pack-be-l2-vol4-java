package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingPreparationService;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PrepareProductRankingTaskletTest {

    @Mock
    private ProductRankingPreparationService preparationService;

    @DisplayName("실행 Parameter로 식별한 상품 랭킹 Snapshot을 준비하고 종료한다.")
    @Test
    void preparesProductRankingSnapshot() {
        // arrange
        PrepareProductRankingTasklet tasklet = new PrepareProductRankingTasklet(
            preparationService,
            "WEEKLY",
            "20260719",
            1L
        );

        // act
        RepeatStatus result = tasklet.execute(null, null);

        // assert
        assertAll(
            () -> verify(preparationService).prepare(
                new ProductRankingSnapshotKey(
                    RankingPeriod.WEEKLY,
                    LocalDate.of(2026, 7, 19),
                    1
                )
            ),
            () -> assertThat(result).isEqualTo(RepeatStatus.FINISHED)
        );
    }
}
