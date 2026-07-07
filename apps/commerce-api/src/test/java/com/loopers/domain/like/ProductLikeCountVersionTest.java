package com.loopers.domain.like;

import com.loopers.infrastructure.like.ProductLikeCountJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @Modifying 쿼리(increase)를 리포지토리에서 직접 호출하므로(파사드 @Transactional 경계 밖) 트랜잭션이 필요
@SpringBootTest
@Transactional
class ProductLikeCountVersionTest {

    @Autowired private LikeCountRepository likeCountRepository;
    @Autowired private ProductLikeCountJpaRepository jpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("increase 두 번이면 count=2, version=2 로 함께 증가한다.")
    @Test
    void versionIncrementsWithCount() {
        likeCountRepository.increase(1L);
        likeCountRepository.increase(1L);

        ProductLikeCount plc = jpaRepository.findByProductId(1L).orElseThrow();
        assertThat(plc.getCount()).isEqualTo(2L);
        assertThat(plc.getVersion()).isEqualTo(2L);
    }
}
