package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;

@Tag(name = "Ranking V1 API", description = "Loopers 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "date(yyyyMMdd) 기준 인기 상품을 점수 내림차순으로 조회합니다. "
            + "period(DAILY/WEEKLY/MONTHLY)로 일간·주간·월간을 선택하며, 생략 시 일간입니다. "
            + "일간에 hour(HH)를 주면 해당 1시간 단위 랭킹을 조회합니다."
    )
    ApiResponse<Page<RankingV1Response>> getRankings(
        String date,
        @Min(0) @Max(23) Integer hour,
        String period,
        @Positive int page,
        // 상한이 없으면 size 하나로 ZSET 전체를 끌어와 상품 조회까지 뒤따른다.
        @Positive @Max(100) int size);
}
