package com.loopers.batch.job.productranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRankingBatchSettingsTest {

  @DisplayName("성능 비교를 위해 양수 Chunk 크기를 설정할 수 있다.")
  @Test
  void acceptsPositiveChunkSize() {
    ProductRankingBatchSettings settings = new ProductRankingBatchSettings(1_000);

    assertThat(settings.chunkSize()).isEqualTo(1_000);
  }

  @DisplayName("0 이하 Chunk 크기는 거부한다.")
  @Test
  void rejectsNonPositiveChunkSize() {
    assertThatThrownBy(() -> new ProductRankingBatchSettings(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1 이상");
  }
}
