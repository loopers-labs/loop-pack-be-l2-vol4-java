package com.loopers.application.like;

public record GetMyLikesCommand(
    Long userId,
    Long authenticatedUserId,
    int page,
    int size
) {

    public boolean isOwnUser() {
        return userId.equals(authenticatedUserId);
    }
}
