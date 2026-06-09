package com.loopers.member.infrastructure;

import com.loopers.member.domain.MemberModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {
    Optional<MemberModel> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
