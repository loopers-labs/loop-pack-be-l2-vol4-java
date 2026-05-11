package com.loopers.domain.member;

public interface MemberRepository {
    Member save(Member member);

    boolean existsByLoginId(String loginId);
}
