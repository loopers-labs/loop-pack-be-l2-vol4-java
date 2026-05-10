package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class MemberModelTest {
    @DisplayName("예시 모델을 생성할 때, ")
    @Nested
    class Create {

        @Mock
        private MemberRepository memberRepository;

        @InjectMocks
        private MemberService memberService;

        @Test
        @DisplayName("신규 아이디로 회원가입 시 저장 성공")
        void register_member_success() {
            // Arrange
            given(memberRepository.existsByUserId("newuser")).willReturn(false);

            // Act & Assert
            assertThatNoException().isThrownBy(() -> memberService.registerMember("newuser", "1234", "new@gmail.com", "홍길동"));
            then(memberRepository).should().save(any(MemberModel.class));
        }

        @Test
        @DisplayName("이미 가입된 로그인 ID 로는 가입하는 경우 예외발생")
        void register_duplicateId_throwsException(){
            // Given
            given(memberRepository.existsByUserId("eccsck")).willReturn(true);
            //when & then
            assertThatThrownBy(() -> memberService.registerMember("eccsck", "1234", "eccsck@gmail.com", "김승찬"))
                    .isInstanceOf(CoreException.class)
                    .hasMessage("이미 존재하는 유저 ID입니다.");
        }
    }
}
