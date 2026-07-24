package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final ProductApplicationService productApplicationService;

    @GetMapping
    public ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "DAILY") String period,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        LocalDate targetDate = parseDate(date);
        RankingPeriod targetPeriod = parsePeriod(period);
        validatePageRequest(page, size);
        return ApiResponse.success(
                PageResult.from(
                        productApplicationService.getRankedProducts(targetDate, targetPeriod, PageRequest.of(page, size))
                                .map(RankingV1Dto.RankingItemResponse::from)
                )
        );
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 필수입니다.");
        }
        try {
            return LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 형식이 올바르지 않습니다: " + date);
        }
    }

    private RankingPeriod parsePeriod(String period) {
        try {
            return RankingPeriod.valueOf(period);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period 값이 올바르지 않습니다: " + period);
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
    }
}
