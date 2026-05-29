package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeFacadeTest {

    private final LikeService likeService = mock(LikeService.class);
    private final UserService userService = mock(UserService.class);
    private final LikeFacade likeFacade = new LikeFacade(likeService, userService);

    @DisplayName("좋아요를 등록하면, 로그인 ID로 유저를 식별해 좋아요 서비스에 위임한다.")
    @Test
    void like() {
        // arrange
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(7L);
        when(userService.getUser("tester")).thenReturn(user);

        // act
        likeFacade.like("tester", 100L);

        // assert
        verify(likeService).like(7L, 100L);
    }

    @DisplayName("좋아요를 취소하면, 로그인 ID로 유저를 식별해 좋아요 서비스에 위임한다.")
    @Test
    void unlike() {
        // arrange
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(7L);
        when(userService.getUser("tester")).thenReturn(user);

        // act
        likeFacade.unlike("tester", 100L);

        // assert
        verify(likeService).unlike(7L, 100L);
    }

    @DisplayName("유저가 존재하지 않으면, 예외가 전파되고 좋아요 처리도 하지 않는다.")
    @Test
    void throwsNotFound_whenUserMissing() {
        // arrange
        when(userService.getUser("ghost")).thenThrow(new CoreException(ErrorType.NOT_FOUND, "회원 없음"));

        // act
        CoreException ex = assertThrows(CoreException.class, () -> likeFacade.like("ghost", 100L));

        // assert
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        verify(likeService, never()).like(anyLong(), anyLong());
    }
}
