package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;
import com.loopers.domain.common.PageResult;

import java.util.List;

public final class LikeV1Dto {

    private LikeV1Dto() {
    }

    public record LikeResponse(Long userId, Long productId) {

        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(info.userId(), info.productId());
        }
    }

    public record LikePageResponse(
            List<LikeResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static LikePageResponse from(PageResult<LikeInfo> result) {
            return new LikePageResponse(
                    result.content().stream().map(LikeResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }
}
