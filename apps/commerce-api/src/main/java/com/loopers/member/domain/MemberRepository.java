package com.loopers.member.domain;

import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);

    Optional<Member> find(Long id);

    Optional<Member> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
