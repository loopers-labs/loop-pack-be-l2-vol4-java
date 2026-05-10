package com.loopers.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;


public interface MemberRepository extends JpaRepository<MemberModel,Long> {
    boolean existsByUserId(String userId);
}
