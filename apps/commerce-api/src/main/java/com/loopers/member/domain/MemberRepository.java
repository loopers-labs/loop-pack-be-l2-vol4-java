package com.loopers.member.domain;

import java.util.Optional;

public interface MemberRepository {
    MemberModel save(MemberModel member);

    Optional<MemberModel> find(Long id);

    Optional<MemberModel> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
