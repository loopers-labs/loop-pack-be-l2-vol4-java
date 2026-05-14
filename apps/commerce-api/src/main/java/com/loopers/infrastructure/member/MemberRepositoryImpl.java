package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    @Override
    public boolean existsByLoginId(String loginId) {
        return memberJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public MemberModel save(MemberModel member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public Optional<MemberModel> findByLoginId(String loginId) {
        return memberJpaRepository.findByLoginId(loginId);
    }
}
