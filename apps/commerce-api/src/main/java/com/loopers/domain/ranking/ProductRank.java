package com.loopers.domain.ranking;

/** 특정 상품의 보드 내 위치. rank 는 1-based (ZREVRANK 의 0-based 를 어댑터가 변환). */
public record ProductRank(long rank, double score) {
}
