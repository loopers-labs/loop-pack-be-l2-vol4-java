package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final UserService userService;

    public void like(String loginId, Long productId) {
        UserModel user = userService.getUser(loginId);
        likeService.like(user.getId(), productId);
    }

    public void unlike(String loginId, Long productId) {
        UserModel user = userService.getUser(loginId);
        likeService.unlike(user.getId(), productId);
    }
}
