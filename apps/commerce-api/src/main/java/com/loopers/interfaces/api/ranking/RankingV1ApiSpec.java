package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "일자별 상품 랭킹 조회 API. 인증 불필요.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "랭킹 페이지 조회",
            description = """
                    일자별/기간별 상품 랭킹을 페이지 단위로 조회합니다.
                    - date: yyyyMMdd 형식 필수. 누락·파싱 실패 시 400을 반환합니다.
                    - period: DAILY(Redis ZSET, 기본값) / WEEKLY·MONTHLY(RDB MV). 열거형 밖의 값이면 400을 반환합니다.
                    - DAILY는 date 당일 ZSET을, WEEKLY/MONTHLY는 date를 as_of_date로 해석한 배치 스냅샷을 조회합니다.
                    - 해당 데이터(ZSET 또는 MV 스냅샷)가 없으면 404를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "date/period 누락 또는 형식 오류",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 일자/기간 랭킹 데이터 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<RankingV1Dto.RankingItemResponse>> getRankings(
            @Parameter(description = "조회 대상 일자 (yyyyMMdd)", required = true) String date,
            @Parameter(description = "조회 기간 (DAILY/WEEKLY/MONTHLY, 기본값: DAILY)") String period,
            @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );
}
