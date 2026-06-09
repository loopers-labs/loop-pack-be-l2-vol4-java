package com.loopers.support.fake;

import com.loopers.member.domain.MemberModel;
import com.loopers.member.domain.MemberRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeMemberRepository implements MemberRepository {

    private final Map<Long, MemberModel> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public MemberModel save(MemberModel member) {
        if (member.getId() == null || member.getId() == 0L) {
            IdFixtures.assignId(member, seq.incrementAndGet());
        }
        store.put(member.getId(), member);
        return member;
    }

    @Override
    public Optional<MemberModel> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<MemberModel> findByLoginId(String loginId) {
        return store.values().stream().filter(m -> m.getLoginId().equals(loginId)).findFirst();
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return store.values().stream().anyMatch(m -> m.getLoginId().equals(loginId));
    }
}
