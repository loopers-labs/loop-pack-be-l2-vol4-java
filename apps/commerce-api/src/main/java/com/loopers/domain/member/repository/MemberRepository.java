package com.loopers.domain.member.repository;

import com.loopers.domain.member.model.Member;
import java.util.Optional;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findByLoginId(String loginId);
}
