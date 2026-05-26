package com.loopers.application.like;

public class LikeCommand {

    public record Like(Long userId, Long productId) {}

    public record Unlike(Long userId, Long productId) {}

    public record GetLiked(Long authenticatedUserId, Long userId) {}
}
