package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("여러 사용자가 동시에 같은 상품에 좋아요를 눌러도 좋아요 수가 정상 반영된다.")
    @Test
    void concurrent_likes_from_multiple_users_are_all_counted() throws InterruptedException {
        // arrange
        BrandModel brand = brandJpaRepository.save(new BrandModel("브랜드", "liketest", "like@test.com"));
        ProductModel product = productJpaRepository.save(
                new ProductModel(brand.getId(), "상품", "설명", 10000L, 100, null)
        );

        int userCount = 50;
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            MemberModel member = memberJpaRepository.save(
                    new MemberModel("likeuser" + i, "Password1!", "like" + i + "@test.com", "김" + i, "19940101")
            );
            memberIds.add(member.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        // act — 50명이 동시에 같은 상품에 좋아요
        for (Long memberId : memberIds) {
            executor.submit(() -> {
                try {
                    likeService.like(memberId, product.getId());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert
        long likeCount = likeService.countLikes(product.getId());
        assertThat(likeCount).isEqualTo(userCount);
    }
}
