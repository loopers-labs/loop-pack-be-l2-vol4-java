package com.loopers.ranking.application;

import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductFacade;
import com.loopers.ranking.domain.MaterializedRankingRepository;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingPage;
import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RankingFacade {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter REQUEST_HOUR_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("uuuuMMddHH")
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
                    .toFormatter()
                    .withResolverStyle(ResolverStyle.STRICT);

    private final RankingRepository rankingRepository;
    private final MaterializedRankingRepository materializedRankingRepository;
    private final ProductFacade productFacade;

    public RankingPageInfo getRankings(
            String period, String date, int page, int size) {
        validatePaging(page, size);
        RankingPeriod rankingPeriod = RankingPeriod.from(period);
        LocalDate rankingDate = parseDate(date, rankingPeriod);

        try {
            RankingPage rankingPage =
                    rankingPeriod == RankingPeriod.DAILY
                            ? rankingRepository.findPage(rankingDate, page, size)
                            : materializedRankingRepository.findPage(
                                    rankingPeriod, rankingDate, page, size);
            return aggregateProducts(rankingPage, page, size);
        } catch (RuntimeException exception) {
            throw new CoreException(
                    ErrorType.SERVICE_UNAVAILABLE,
                    "랭킹 정보를 조회할 수 없습니다.",
                    exception);
        }
    }

    /** 기존 애플리케이션 호출자의 일간 랭킹 계약을 유지한다. */
    public RankingPageInfo getRankings(String date, int page, int size) {
        return getRankings(null, date, page, size);
    }

    public RankingPageInfo getHourlyRankings(String dateTime, int page, int size) {
        validatePaging(page, size);
        LocalDateTime rankingDateTime = parseDateTime(dateTime);

        try {
            RankingPage rankingPage =
                    rankingRepository.findHourlyPage(rankingDateTime, page, size);
            return aggregateProducts(rankingPage, page, size);
        } catch (RuntimeException exception) {
            throw new CoreException(
                    ErrorType.SERVICE_UNAVAILABLE,
                    "시간 랭킹 정보를 조회할 수 없습니다.",
                    exception);
        }
    }

    private RankingPageInfo aggregateProducts(RankingPage rankingPage, int page, int size) {
        List<Long> productIds =
                rankingPage.entries().stream().map(RankingEntry::productId).toList();
        Map<Long, ProductDetailInfo> productsById =
                productFacade.getExistingProductDetails(productIds).stream()
                        .collect(
                                Collectors.toMap(
                                        ProductDetailInfo::id,
                                        Function.identity(),
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        List<RankingItemInfo> items =
                rankingPage.entries().stream()
                        .filter(entry -> productsById.containsKey(entry.productId()))
                        .map(
                                entry ->
                                        new RankingItemInfo(
                                                entry.rank(),
                                                entry.score(),
                                                productsById.get(entry.productId())))
                        .toList();
        int totalPages =
                size == 0 ? 0 : (int) Math.ceil((double) rankingPage.totalCount() / size);

        return new RankingPageInfo(items, page, size, rankingPage.totalCount(), totalPages);
    }

    private LocalDate parseDate(String date, RankingPeriod period) {
        if (date == null || date.isBlank()) {
            LocalDate today = LocalDate.now(SEOUL_ZONE);
            return period == RankingPeriod.DAILY ? today : today.minusDays(1);
        }
        try {
            return LocalDate.parse(date, REQUEST_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식의 유효한 날짜여야 합니다.");
        }
    }

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "datetime은 필수입니다.");
        }
        try {
            return LocalDateTime.parse(dateTime, REQUEST_HOUR_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST, "datetime은 yyyyMMddHH 형식의 유효한 일시여야 합니다.");
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
    }
}
