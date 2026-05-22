package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findByLoginId(String loginId);
}
