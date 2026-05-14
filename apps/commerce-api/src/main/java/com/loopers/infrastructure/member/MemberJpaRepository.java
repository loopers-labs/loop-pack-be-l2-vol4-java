package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String loginId);
}
