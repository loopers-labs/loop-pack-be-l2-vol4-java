package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import org.springframework.data.jpa.repository.JpaRepository;


public interface MemberJpaRepository extends JpaRepository<MemberModel,Long> {
    boolean existsByUserId(String userId);
}
