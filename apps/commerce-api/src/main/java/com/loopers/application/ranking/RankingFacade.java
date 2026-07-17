package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPage;
import com.loopers.domain.ranking.RankingRepository;
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
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
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
  private final ProductService productService;

  public RankingPageInfo getRankings(String date, int page, int size) {
    validatePaging(page, size);
    LocalDate rankingDate = parseDate(date);

    RankingPage rankingPage;
    try {
      rankingPage = rankingRepository.findPage(rankingDate, page, size);
    } catch (RuntimeException e) {
      throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "랭킹 정보를 조회할 수 없습니다.");
    }

    return aggregateProducts(rankingPage, page, size);
  }

  public RankingPageInfo getHourlyRankings(String dateTime, int page, int size) {
    validatePaging(page, size);
    LocalDateTime rankingDateTime = parseDateTime(dateTime);

    RankingPage rankingPage;
    try {
      rankingPage = rankingRepository.findHourlyPage(rankingDateTime, page, size);
    } catch (RuntimeException e) {
      throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "시간 랭킹 정보를 조회할 수 없습니다.");
    }

    return aggregateProducts(rankingPage, page, size);
  }

  private RankingPageInfo aggregateProducts(RankingPage rankingPage, int page, int size) {
    List<Long> productIds = rankingPage.entries().stream().map(RankingEntry::productId).toList();
    Map<Long, ProductInfo> productsById =
        productService.getActiveProducts(productIds).stream()
            .map(ProductInfo::from)
            .collect(
                Collectors.toMap(
                    ProductInfo::id,
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));

    List<RankingItemInfo> items =
        rankingPage.entries().stream()
            .filter(entry -> productsById.containsKey(entry.productId()))
            .map(
                entry ->
                    new RankingItemInfo(
                        entry.rank(), entry.score(), productsById.get(entry.productId())))
            .toList();
    int totalPages = size == 0 ? 0 : (int) Math.ceil((double) rankingPage.totalCount() / size);

    return new RankingPageInfo(items, page, size, rankingPage.totalCount(), totalPages);
  }

  private LocalDate parseDate(String date) {
    if (date == null || date.isBlank()) {
      return LocalDate.now(SEOUL_ZONE);
    }
    try {
      return LocalDate.parse(date, REQUEST_DATE_FORMATTER);
    } catch (DateTimeParseException e) {
      throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식의 유효한 날짜여야 합니다.");
    }
  }

  private LocalDateTime parseDateTime(String dateTime) {
    if (dateTime == null || dateTime.isBlank()) {
      throw new CoreException(ErrorType.BAD_REQUEST, "datetime은 필수입니다.");
    }
    try {
      return LocalDateTime.parse(dateTime, REQUEST_HOUR_FORMATTER);
    } catch (DateTimeParseException e) {
      throw new CoreException(ErrorType.BAD_REQUEST, "datetime은 yyyyMMddHH 형식의 유효한 일시여야 합니다.");
    }
  }

  private void validatePaging(int page, int size) {
    if (page < 1) {
      throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
    }
    if (size < 1 || size > 100) {
      throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 100 이하여야 합니다.");
    }
  }
}
