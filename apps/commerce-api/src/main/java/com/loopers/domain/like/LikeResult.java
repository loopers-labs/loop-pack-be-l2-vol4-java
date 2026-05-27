package com.loopers.domain.like;

public enum LikeResult {
    APPLIED,  // 실제로 변경이 일어남 (좋아요 등록됨 or 취소됨)
    IGNORED;  // 멱등하게 무시됨 (이미 존재함 or 존재하지 않음)

    public boolean isApplied() {
        return this == APPLIED;
    }
}
