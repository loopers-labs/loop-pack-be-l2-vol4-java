package com.loopers.like.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LikeErrorCode implements ErrorCode {
    ALREADY_LIKED("LIKE_ALREADY_LIKED", "이미 좋아요한 상품입니다.");

    private final String code;
    private final String message;
}
