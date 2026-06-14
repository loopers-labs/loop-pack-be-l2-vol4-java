package com.loopers.like.application;

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
