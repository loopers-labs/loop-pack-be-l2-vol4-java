package com.loopers.like.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.BrandModel;
import com.loopers.member.application.MemberService;
import com.loopers.member.domain.MemberModel;
import com.loopers.product.application.ProductService;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fake.FakeBrandRepository;
import com.loopers.support.fake.FakeLikeRepository;
import com.loopers.support.fake.FakeMemberRepository;
import com.loopers.support.fake.FakeProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeFacadeTest {

    private FakeLikeRepository likeRepository;
    private LikeService likeService;
    private LikeFacade likeFacade;
    private Long memberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        likeRepository = new FakeLikeRepository();
        FakeMemberRepository memberRepository = new FakeMemberRepository();
        FakeProductRepository productRepository = new FakeProductRepository();
        FakeBrandRepository brandRepository = new FakeBrandRepository();

        likeService = new LikeService(likeRepository);
        MemberService memberService = new MemberService(memberRepository);
        ProductService productService = new ProductService(productRepository);
        BrandService brandService = new BrandService(brandRepository);

        likeFacade =
            new LikeFacade(likeService, memberService, productService, brandService);

        MemberModel member = memberRepository.save(new MemberModel("member01", "pw123456"));
        BrandModel brand = brandRepository.save(new BrandModel("브랜드", "설명"));
        ProductModel product =
            productRepository.save(new ProductModel(brand.getId(), "상품", "설명", 1_000L, 10));
        memberId = member.getId();
        productId = product.getId();
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
