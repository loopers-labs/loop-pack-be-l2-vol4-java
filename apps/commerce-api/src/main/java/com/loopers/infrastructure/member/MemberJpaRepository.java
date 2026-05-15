package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {
    boolean existsByLoginId(String loginId);
    Optional<MemberModel> findByLoginId(String loginId);
}
