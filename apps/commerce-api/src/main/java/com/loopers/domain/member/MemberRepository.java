package com.loopers.domain.member;

public interface MemberRepository {
    boolean existsByLoginId(String loginId);
    void save(Member member);
}
