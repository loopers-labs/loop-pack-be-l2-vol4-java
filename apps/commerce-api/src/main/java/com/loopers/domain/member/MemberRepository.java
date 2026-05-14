package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    boolean existsByLoginId(String loginId);
    void save(Member member);
    Optional<Member> findByLoginId(String loginId);
}
