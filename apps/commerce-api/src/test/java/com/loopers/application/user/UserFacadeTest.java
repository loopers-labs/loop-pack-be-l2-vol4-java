package com.loopers.application.user;

import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserFacade userFacade;

    @DisplayName("내 정보 조회를 할 때,")
    @Nested
    class GetMyInfo {

        // E2E에서 존재하지 않는 ID 요청 시 LoginUserResolver가 먼저 차단하여
        // UserFacade.getMyInfo()의 NOT_FOUND 경로가 실제로 도달되지 않는 갭을 보완
        @DisplayName("해당 loginId의 회원이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // arrange
            given(userService.findByLoginId("nonexistent")).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userFacade.getMyInfo("nonexistent")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
