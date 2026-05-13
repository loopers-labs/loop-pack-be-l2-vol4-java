package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {
    boolean existsByLoginId(String loginId);
}
