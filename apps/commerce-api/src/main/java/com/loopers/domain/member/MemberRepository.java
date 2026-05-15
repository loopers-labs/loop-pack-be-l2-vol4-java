package com.loopers.domain.member;

import java.util.Optional;

public interface MemberRepository {
    boolean existsByLoginId(String loginId);
    MemberModel save(MemberModel member);
    Optional<MemberModel> findByLoginId(String loginId);
}
