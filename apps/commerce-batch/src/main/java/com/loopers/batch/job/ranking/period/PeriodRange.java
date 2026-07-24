package com.loopers.batch.job.ranking.period;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 집계 대상 구간(시작일 ~ 종료일, 양끝 포함).
 *
 * <p><b>파라미터 없이 돌리면 "직전 확정 기간"</b>을 잡는다 — 주간이면 지난주, 월간이면 지난달.
 * 진행 중인 기간을 집계하면 순위가 매일 뒤집히고, 조회하는 쪽은 그게 확정값인지 중간값인지 알 수 없다.
 * 확정된 구간만 MV 에 올린다는 게 이 배치의 계약이다.
 *
 * <p>재집계(백필)가 필요하면 {@code baseDate=yyyyMMdd} 로 그 날짜가 속한 기간을 직접 지정한다.
 */
public record PeriodRange(LocalDate start, LocalDate end) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * job parameter {@code baseDate}(yyyyMMdd)가 있으면 그 날짜가 속한 기간, 없으면 직전 확정 기간.
     *
     * @param baseDate {@code null}/공백이면 직전 기간으로 폴백
     */
    public static PeriodRange resolve(RankingPeriodType type, String baseDate) {
        LocalDate start = (baseDate == null || baseDate.isBlank())
                ? type.previousOf(LocalDate.now(KST))
                : type.resolveStart(LocalDate.parse(baseDate, YYYYMMDD));
        return new PeriodRange(start, type.resolveEnd(start));
    }
}
