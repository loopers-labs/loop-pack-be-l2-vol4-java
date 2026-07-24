package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RankingV1Controller.class)
class RankingV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingFacade rankingFacade;

    @Test
    @DisplayName("랭킹 페이지 조회 API 요청 시 상품 정보가 포함된 랭킹 PageResponse를 반환한다.")
    void getRankings_ShouldReturnRankingPage() throws Exception {
        // given
        RankingProductInfo info = new RankingProductInfo(
            1,
            10.0,
            1L,
            "Air Max",
            10L,
            "Nike",
            new BigDecimal("1000.0000")
        );
        given(rankingFacade.getRankings(
            com.loopers.domain.ranking.RankingPeriod.DAILY,
            "20260714",
            "20260714",
            1,
            20
        ))
            .willReturn(new PageImpl<>(List.of(info), PageRequest.of(0, 20), 1));

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                .param("date", "20260714")
                .param("period", "DAILY")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content[0].rank").value(1))
            .andExpect(jsonPath("$.data.content[0].score").value(10.0))
            .andExpect(jsonPath("$.data.content[0].productId").value(1))
            .andExpect(jsonPath("$.data.content[0].productName").value("Air Max"))
            .andExpect(jsonPath("$.data.content[0].brandName").value("Nike"))
            .andExpect(jsonPath("$.data.content[0].price").value(1000.0000))
            .andExpect(jsonPath("$.data.pageNumber").value(0))
            .andExpect(jsonPath("$.data.pageSize").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("date가 없으면 서버 오늘 날짜로 랭킹 페이지를 조회한다.")
    void getRankings_WhenDateMissing_ShouldUseToday() throws Exception {
        // given
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        given(rankingFacade.getRankings(
            com.loopers.domain.ranking.RankingPeriod.DAILY,
            today,
            today,
            1,
            20
        ))
            .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

        verify(rankingFacade).getRankings(
            com.loopers.domain.ranking.RankingPeriod.DAILY,
            today,
            today,
            1,
            20
        );
    }

    @Test
    @DisplayName("period와 startDate/endDate가 있으면 해당 기간 랭킹을 조회한다.")
    void getRankings_WhenPeriodRangeGiven_ShouldQueryRange() throws Exception {
        // given
        given(rankingFacade.getRankings(
            com.loopers.domain.ranking.RankingPeriod.WEEKLY,
            "20260720",
            "20260726",
            1,
            20
        )).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v1/rankings")
                .param("period", "WEEKLY")
                .param("startDate", "20260720")
                .param("endDate", "20260726")
                .param("page", "1")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

        verify(rankingFacade).getRankings(
            com.loopers.domain.ranking.RankingPeriod.WEEKLY,
            "20260720",
            "20260726",
            1,
            20
        );
    }

    @Test
    @DisplayName("DAILY 기간의 startDate와 endDate가 다르면 BAD_REQUEST를 반환한다.")
    void getRankings_WhenInvalidDailyRange_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                .param("period", "DAILY")
                .param("startDate", "20260720")
                .param("endDate", "20260721")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("WEEKLY 기간이 월요일부터 일요일까지 7일이 아니면 BAD_REQUEST를 반환한다.")
    void getRankings_WhenInvalidWeeklyRange_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                .param("period", "WEEKLY")
                .param("startDate", "20260721")
                .param("endDate", "20260727")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("MONTHLY 기간이 월초부터 월말까지가 아니면 BAD_REQUEST를 반환한다.")
    void getRankings_WhenInvalidMonthlyRange_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                .param("period", "MONTHLY")
                .param("startDate", "20260702")
                .param("endDate", "20260731")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @Test
    @DisplayName("WEEKLY/MONTHLY 요청에서 startDate 또는 endDate가 없으면 BAD_REQUEST를 반환한다.")
    void getRankings_WhenRangePeriodWithoutRange_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rankings")
                .param("period", "WEEKLY")
                .param("date", "20260720")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }
}
