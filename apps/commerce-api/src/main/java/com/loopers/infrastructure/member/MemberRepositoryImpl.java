package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public MemberModel save(MemberModel member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public Optional<MemberModel> find(Long id) {
        return memberJpaRepository.findById(id);
    }

    @Override
    public List<MemberModel> findAll() {
        return memberJpaRepository.findAll();
    }

    @Override
    public void delete(Long id) {
        memberJpaRepository.deleteById(id);
    }
}
