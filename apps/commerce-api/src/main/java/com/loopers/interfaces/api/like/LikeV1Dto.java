package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

import java.util.List;

public class LikeV1Dto {

    public record LikedProductResponse(
        Long likeId,
        Long productId,
        String productName,
        Long price,
        Long likeCount
    ) {
        public static LikedProductResponse from(LikeInfo info) {
            return new LikedProductResponse(
                info.likeId(),
                info.productId(),
                info.productName(),
                info.price(),
                info.likeCount()
            );
        }
    }

    public record LikedProductListResponse(List<LikedProductResponse> likes) {
        public static LikedProductListResponse from(List<LikeInfo> infos) {
            return new LikedProductListResponse(infos.stream().map(LikedProductResponse::from).toList());
        }
    }
}
