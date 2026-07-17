package com.loopers.ranking.interfaces;

import com.loopers.ranking.application.RankingRebuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 랭킹 재구축 운영 API. Redis 소실 시 담당자가 오늘 판을 어제 스냅샷에서 수동 복구한다.
 */
@RestController
@RequestMapping("/api/v1/admin/rankings")
@RequiredArgsConstructor
public class RankingRebuildController {

    private final RankingRebuildService rankingRebuildService;

    @PostMapping("/rebuild")
    public RebuildResult rebuild() {
        return new RebuildResult(rankingRebuildService.rebuildToday());
    }

    public record RebuildResult(int seededCount) {
    }
}
