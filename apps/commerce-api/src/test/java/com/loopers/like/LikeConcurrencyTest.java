package com.loopers.like;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.like.application.LikeFacade;
import com.loopers.like.infrastructure.LikeJpaRepository;
import com.loopers.member.domain.Member;
import com.loopers.member.infrastructure.MemberJpaRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.ConcurrencyTestSupport;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private LikeJpaRepository likeJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("여러 회원이 동시에 같은 상품에 좋아요를 요청해도 좋아요 수가 정확히 반영된다.")
    @Test
    void concurrentLikes_areCountedExactly() throws InterruptedException {
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "상품", "설명", 1_000L));

        int threads = 30;
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Member member =
                memberJpaRepository.save(new Member(String.format("user%04d", i), "pw123456"));
            memberIds.add(member.getId());
        }

        ConcurrencyTestSupport.runConcurrently(
            threads, idx -> likeFacade.registerLike(memberIds.get(idx), product.getId()));

        assertThat(likeJpaRepository.countByProductId(product.getId())).isEqualTo(threads);
    }

    @DisplayName("같은 회원이 동시에 같은 상품에 좋아요를 중복 요청해도 좋아요 수는 1로 유지된다.")
    @Test
    void concurrentDuplicateLikes_bySameMember_stayOne() throws InterruptedException {
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "상품", "설명", 1_000L));
        Member member = memberJpaRepository.save(new Member("user0001", "pw123456"));

        ConcurrencyTestSupport.runConcurrently(
            20, idx -> likeFacade.registerLike(member.getId(), product.getId()));

        assertThat(likeJpaRepository.countByProductId(product.getId())).isEqualTo(1L);
    }
}
