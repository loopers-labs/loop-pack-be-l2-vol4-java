package com.loopers.domain.member;

import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberJpaRepository memberJpaRepository;

    public MemberModel registerMember(String userId, String password, String email, String username, String birthDate) {
        if (memberJpaRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 유저 ID입니다.");
        }
        return memberJpaRepository.save(new MemberModel(userId, password, email, username, birthDate));
    }

    public MemberModel getMember(Long id) {
        return memberJpaRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }

    @Transactional
    public void updatePassword(Long id, String currentPassword, String newPassword) {
        MemberModel member = getMember(id);
        member.updatePassword(currentPassword, newPassword);
    }
}
