package com.loopers.application.like;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.like.model.Like;
import com.loopers.domain.like.service.LikeDomainService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.Password;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LikeFacadeTest {

    private MemberService memberService;
    private LikeApplicationService likeApplicationService;
    private LikeDomainService likeDomainService;
    private ProductDomainService productDomainService;
    private BrandDomainService brandDomainService;
    private LikeFacade likeFacade;

    private Member member;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        likeApplicationService = mock(LikeApplicationService.class);
        likeDomainService = mock(LikeDomainService.class);
        productDomainService = mock(ProductDomainService.class);
        brandDomainService = mock(BrandDomainService.class);
        likeFacade = new LikeFacade(memberService, likeApplicationService, likeDomainService, productDomainService, brandDomainService);

        Password password = Password.of("Password1!", "1990-01-01", new BCryptPasswordEncoder().encode("Password1!"));
        member = new Member("user1", password, "홍길동", "1990-01-01", "test@example.com");
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class AddLike {

        @DisplayName("유효한 요청이면, likeApplicationService.addLike()가 호출된다.")
        @Test
        void callsAddLike_whenRequestIsValid() {
            // Arrange
            when(memberService.getMember("user1")).thenReturn(member);

            // Act
            likeFacade.addLike("user1", 2L);

            // Assert
            verify(likeApplicationService).addLike(member.getId(), 2L);
        }

        @DisplayName("존재하지 않는 loginId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberDoesNotExist() {
            // Arrange
            when(memberService.getMember("unknown")).thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                likeFacade.addLike("unknown", 2L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeApplicationService, never()).addLike(anyLong(), anyLong());
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class RemoveLike {

        @DisplayName("유효한 요청이면, likeApplicationService.removeLike()가 호출된다.")
        @Test
        void callsRemoveLike_whenRequestIsValid() {
            // Arrange
            when(memberService.getMember("user1")).thenReturn(member);

            // Act
            likeFacade.removeLike("user1", 2L);

            // Assert
            verify(likeApplicationService).removeLike(member.getId(), 2L);
        }
    }

    @DisplayName("좋아요 목록을 조회할 때, ")
    @Nested
    class GetLikes {

        @DisplayName("유효한 요청이면, 상품명과 브랜드명이 포함된 LikeInfo 목록을 반환한다.")
        @Test
        void returnsLikeInfoList_whenRequestIsValid() {
            // Arrange
            Long targetUserId = member.getId();
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            Brand brand = Brand.create("나이키");
            List<Like> likes = List.of(Like.create(targetUserId, 10L));

            when(memberService.getMember("user1")).thenReturn(member);
            when(likeDomainService.getLikes(member.getId(), targetUserId)).thenReturn(likes);
            when(productDomainService.getProduct(10L)).thenReturn(product);
            when(brandDomainService.getBrand(product.getBrandId())).thenReturn(brand);

            // Act
            List<LikeInfo> result = likeFacade.getLikes("user1", targetUserId);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).productName()).isEqualTo("에어맥스");
            assertThat(result.get(0).brandName()).isEqualTo("나이키");
            assertThat(result.get(0).price()).isEqualTo(100_000L);
        }

        @DisplayName("타인의 좋아요 목록을 조회하면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenRequestUserDiffersFromTargetUser() {
            // Arrange
            when(memberService.getMember("user1")).thenReturn(member);
            when(likeDomainService.getLikes(member.getId(), 99L))
                .thenThrow(new CoreException(ErrorType.FORBIDDEN, "다른 유저의 좋아요 목록은 조회할 수 없습니다."));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                likeFacade.getLikes("user1", 99L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
