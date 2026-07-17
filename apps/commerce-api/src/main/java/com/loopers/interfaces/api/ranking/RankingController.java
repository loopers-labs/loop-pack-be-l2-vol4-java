package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class RankingController {

    private final RankingFacade rankingFacade;

    @GetMapping("/api/v1/rankings")
    public ApiResponse<RankingDto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        LocalDate targetDate = (date == null || date.isBlank())
            ? LocalDate.now()
            : LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);

        RankingPageInfo result = rankingFacade.getRankings(targetDate, page, size);
        List<RankingDto.RankingItemResponse> rankings = result.items().stream()
            .map(RankingDto.RankingItemResponse::from)
            .toList();

        return ApiResponse.success(new RankingDto.RankingPageResponse(
            targetDate.format(DateTimeFormatter.BASIC_ISO_DATE), rankings, result.totalElements(), result.totalPages()
        ));
    }
}
