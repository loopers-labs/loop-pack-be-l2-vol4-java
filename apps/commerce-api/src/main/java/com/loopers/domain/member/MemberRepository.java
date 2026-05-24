package com.loopers.domain.member;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    MemberModel save(MemberModel member);
    Optional<MemberModel> find(Long id);
    List<MemberModel> findAll();
    void delete(Long id);
    boolean existsByUserId(String userId);
}
