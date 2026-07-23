package com.loopers.tddstudy.interfaces.api.ranking;

import com.loopers.tddstudy.application.ranking.RankingInfo;
import com.loopers.tddstudy.application.queue.EntryTokenService;
import com.loopers.tddstudy.application.ranking.RankingService;
import com.loopers.tddstudy.domain.ranking.RankingPeriodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RankingV1Controller.class)
class RankingV1ControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RankingService rankingService;

    // WebConfig가 등록하는 인터셉터의 의존성 — /api/v1/orders 에만 적용되어 랭킹 조회엔 영향 없음
    @MockitoBean
    EntryTokenService entryTokenService;

    @Test
    @DisplayName("period를 지정하지 않으면 일간 랭킹을 조회한다")
    void defaults_to_daily() throws Exception {
        when(rankingService.getRankingPage(eq(RankingPeriodType.DAILY), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new RankingInfo(1, 101L, "운동화", 50000, 0.7)));

        mockMvc.perform(get("/api/v1/rankings").param("date", "20260714"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("DAILY"))
                .andExpect(jsonPath("$.periodKey").value("20260714"))
                .andExpect(jsonPath("$.items[0].productId").value(101))
                .andExpect(jsonPath("$.items[0].name").value("운동화"));
    }

    @Test
    @DisplayName("주간 랭킹은 직전에 끝난 주의 키를 함께 반환한다")
    void weekly_returns_last_completed_week_key() throws Exception {
        when(rankingService.getRankingPage(eq(RankingPeriodType.WEEKLY), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new RankingInfo(1, 101L, "운동화", 50000, 9.0)));

        mockMvc.perform(get("/api/v1/rankings")
                        .param("period", "WEEKLY")
                        .param("date", "20260706"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEKLY"))
                .andExpect(jsonPath("$.periodKey").value("2026-W27"))   // 6/29~7/5
                .andExpect(jsonPath("$.items[0].score").value(9.0));
    }

    @Test
    @DisplayName("월간 랭킹은 직전에 끝난 달의 키를 반환한다")
    void monthly_returns_last_completed_month_key() throws Exception {
        when(rankingService.getRankingPage(eq(RankingPeriodType.MONTHLY), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rankings")
                        .param("period", "MONTHLY")
                        .param("date", "20260801"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.periodKey").value("2026-07"));
    }

    @Test
    @DisplayName("페이지 정보가 응답에 그대로 담긴다")
    void echoes_paging_info() throws Exception {
        when(rankingService.getRankingPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rankings")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    @DisplayName("지원하지 않는 period는 400을 반환한다")
    void invalid_period_returns_400() throws Exception {
        mockMvc.perform(get("/api/v1/rankings").param("period", "YEARLY"))
                .andExpect(status().isBadRequest());
    }
}
