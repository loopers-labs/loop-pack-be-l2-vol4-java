package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 진입 관문 — 유저가 유효한(미만료) 입장 토큰을 보유했는지 검증한다. (결정 #4)
 * 토큰 키(entry-token:{userId})의 쓰기 주체는 입장 스케줄러뿐이라 "키 존재 = 입장 허가된 본인"이 성립한다(presence-check).
 * 없거나 만료(find 가 빈 Optional)면 거부 → advice 가 403 으로 매핑.
 */
@Component
@RequiredArgsConstructor
public class EntryTokenValidator {

    private final EntryTokenRepository entryTokenRepository;

    /** 유효한 입장 토큰이 없으면 CoreException(ENTRY_TOKEN_INVALID). */
    public void validate(Long userId) {
        entryTokenRepository.find(userId)
            .orElseThrow(() -> new CoreException(ErrorType.ENTRY_TOKEN_INVALID));
    }
}
