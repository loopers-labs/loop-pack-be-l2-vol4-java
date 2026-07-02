package com.loopers.like.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.member.domain.Member;
import com.loopers.member.infrastructure.MemberJpaRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeFacadeTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private LikeService likeService;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long memberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        Member member = memberJpaRepository.save(new Member("member01", "pw123456"));
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "상품", "설명", 1_000L));
        memberId = member.getId();
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록/취소 흐름에서,")
    @Nested
    class Flow {
        @DisplayName("좋아요를 등록하면 좋아요 수가 1 증가한다.")
        @Test
        void registersLike() {
            likeFacade.registerLike(memberId, productId);
            assertThat(likeService.getLikeCount(productId)).isEqualTo(1L);
        }

        @DisplayName("같은 상품에 중복 등록해도 멱등하게 1개만 유지된다.")
        @Test
        void registerIsIdempotent() {
            likeFacade.registerLike(memberId, productId);
            likeFacade.registerLike(memberId, productId);
            assertThat(likeService.getLikeCount(productId)).isEqualTo(1L);
        }

        @DisplayName("좋아요를 취소하면 좋아요 수가 0이 된다.")
        @Test
        void cancelsLike() {
            likeFacade.registerLike(memberId, productId);
            likeFacade.cancelLike(memberId, productId);
            assertThat(likeService.getLikeCount(productId)).isZero();
        }

        @DisplayName("등록되지 않은 좋아요를 취소해도 멱등하게 성공한다.")
        @Test
        void cancelIsIdempotent() {
            likeFacade.cancelLike(memberId, productId);
            assertThat(likeService.getLikeCount(productId)).isZero();
        }
    }

    @DisplayName("예외 흐름에서,")
    @Nested
    class Failure {
        @DisplayName("존재하지 않는 회원이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberMissing() {
            CoreException result =
                assertThrows(CoreException.class, () -> likeFacade.registerLike(999L, productId));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            CoreException result =
                assertThrows(CoreException.class, () -> likeFacade.registerLike(memberId, 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
