package com.loopers.domain.queue;

import java.util.List;

/**
 * 입장 토큰 발급 실패분(Dead Letter) 저장소. Redis List로 구현된다.
 * <p>
 * ZPOPMIN으로 대기열에서 이미 <b>빠져나온</b>(=자리를 딴) 유저인데 토큰 발급이 실패한 경우,
 * 메인 대기열에 되돌리면 순번이 역전된다. 대신 여기(별도 실패함)에 담고 재시도만 반복한다.
 * 재처리는 "순번 재경쟁"이 아니라 {@code issue}만 다시 시도하는 것이므로 순번을 건드리지 않는다.
 */
public interface EntryTokenDlqRepository {

    /** 발급 실패한 userId를 실패함 뒤에 넣는다(RPUSH). */
    void push(Long userId);

    /**
     * 실패함 앞에서 최대 {@code count}개를 꺼낸다(LPOP, FIFO).
     *
     * @return 꺼낸 userId 목록. 비어 있으면 빈 목록.
     */
    List<Long> drain(long count);
}
