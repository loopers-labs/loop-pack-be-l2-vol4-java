package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);

    boolean existsByLoginId(String loginId);

    Optional<Member> findByLoginId(String loginId);
}
