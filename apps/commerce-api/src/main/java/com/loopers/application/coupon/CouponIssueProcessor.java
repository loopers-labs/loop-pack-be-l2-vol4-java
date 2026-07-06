package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueProcessor {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRedisStore couponIssueRedisStore;

    @Value("${coupon.issue.processor.metrics-enabled:false}")
    private boolean metricsEnabled;

    @Transactional
    public void process(String requestId) {
        processBatch(List.of(requestId));
    }

    @Transactional
    public void processBatch(List<String> requestIds) {
        long txStartedAt = System.nanoTime();
        long[] methodCompletedAt = new long[1];
        registerMetricsAfterCompletion(requestIds.size(), txStartedAt, methodCompletedAt);

        try {
            if (requestIds.isEmpty()) {
                return;
            }

            long findRequestsStartedAt = System.nanoTime();
            Map<String, CouponIssueRequest> byId = couponIssueRequestRepository.findByRequestIdIn(requestIds).stream()
                .collect(Collectors.toMap(CouponIssueRequest::getRequestId, Function.identity()));
            long findRequestsMillis = elapsedMillis(findRequestsStartedAt);

            List<CouponIssueRequest> ordered = new ArrayList<>();
            for (String requestId : requestIds) {
                CouponIssueRequest request = byId.get(requestId);
                if (request == null) {
                    log.warn("[CouponIssue] request not found. requestId={}", requestId);
                    continue;
                }
                if (!request.isTerminal()) {
                    ordered.add(request);
                }
            }
            if (ordered.isEmpty()) {
                return;
            }

            Map<Long, List<CouponIssueRequest>> byCoupon = ordered.stream()
                .collect(Collectors.groupingBy(CouponIssueRequest::getCouponId, LinkedHashMap::new, Collectors.toList()));
            byCoupon.keySet().stream().sorted()
                .forEach(couponId -> processCouponGroup(couponId, byCoupon.get(couponId)));
            logMetrics("method", "batchSize={} findRequestsMs={} methodMs={}",
                requestIds.size(), findRequestsMillis, elapsedMillis(txStartedAt));
        } finally {
            methodCompletedAt[0] = System.nanoTime();
        }
    }

    private void processCouponGroup(Long couponId, List<CouponIssueRequest> requests) {
        long startedAt = System.nanoTime();

        long couponLookupStartedAt = System.nanoTime();
        CouponModel template = couponRepository.findById(couponId).orElse(null);
        long couponLookupMillis = elapsedMillis(couponLookupStartedAt);
        if (template == null) {
            requests.forEach(request -> failAndRelease(request, "荑좏룿??議댁옱?섏? ?딆뒿?덈떎."));
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (template.isExpired(now)) {
            requests.forEach(request -> failAndRelease(request, "留뚮즺??荑좏룿?낅땲??"));
            return;
        }

        List<Long> userIds = requests.stream().map(CouponIssueRequest::getUserId).toList();
        long duplicateLookupStartedAt = System.nanoTime();
        Set<Long> issuedUsers = new HashSet<>(userCouponRepository.findIssuedUserIds(couponId, userIds));
        long duplicateLookupMillis = elapsedMillis(duplicateLookupStartedAt);

        List<UserCouponModel> toIssue = new ArrayList<>();
        List<CouponIssueRequest> winners = new ArrayList<>();
        long issueLoopStartedAt = System.nanoTime();
        for (CouponIssueRequest request : requests) {
            if (!issuedUsers.add(request.getUserId())) {
                failAndRelease(request, "?대? 諛쒓툒諛쏆? 荑좏룿?낅땲??");
                continue;
            }
            toIssue.add(UserCouponModel.issue(request.getUserId(), template));
            winners.add(request);
        }
        long issueLoopMillis = elapsedMillis(issueLoopStartedAt);

        if (toIssue.isEmpty()) {
            return;
        }

        long saveAllStartedAt = System.nanoTime();
        List<UserCouponModel> issued = userCouponRepository.saveAll(toIssue);
        long saveAllMillis = elapsedMillis(saveAllStartedAt);

        long countUpdateStartedAt = System.nanoTime();
        couponRepository.increaseIssuedCount(couponId, issued.size());
        long countUpdateMillis = elapsedMillis(countUpdateStartedAt);

        long markIssuedStartedAt = System.nanoTime();
        for (int i = 0; i < winners.size(); i++) {
            CouponIssueRequest winner = winners.get(i);
            winner.markIssued(issued.get(i).getId());
            runAfterCommit(() -> couponIssueRedisStore.confirmIssue(
                winner.getCouponId(), winner.getUserId(), winner.getRequestId()));
        }
        log.info("[CouponIssue] issued {} coupons. couponId={}, batchSize={}",
            issued.size(), couponId, requests.size());
        logMetrics(
            "group",
            "couponId={} batchSize={} issued={} couponLookupMs={} duplicateLookupMs={} issueLoopMs={} saveAllCallMs={} countUpdateMs={} markIssuedMs={} groupMethodMs={}",
            couponId,
            requests.size(),
            issued.size(),
            couponLookupMillis,
            duplicateLookupMillis,
            issueLoopMillis,
            saveAllMillis,
            countUpdateMillis,
            elapsedMillis(markIssuedStartedAt),
            elapsedMillis(startedAt)
        );
    }

    private void failAndRelease(CouponIssueRequest request, String reason) {
        request.markFailed(reason);
        runAfterCommit(() -> couponIssueRedisStore.cancelReservation(
            request.getCouponId(), request.getUserId(), request.getRequestId()));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void registerMetricsAfterCompletion(int batchSize, long txStartedAt, long[] methodCompletedAt) {
        if (!metricsEnabled || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                long completedAt = System.nanoTime();
                long methodCompleted = methodCompletedAt[0] == 0 ? completedAt : methodCompletedAt[0];
                log.info("[CouponIssueMetric] phase=tx batchSize={} txMs={} flushCommitAndAfterCommitMs={} status={}",
                    batchSize,
                    nanosToMillis(completedAt - txStartedAt),
                    nanosToMillis(completedAt - methodCompleted),
                    status);
            }
        });
    }

    private void logMetrics(String phase, String message, Object... arguments) {
        if (!metricsEnabled) {
            return;
        }
        log.info("[CouponIssueMetric] phase={} " + message, prepend(phase, arguments));
    }

    private Object[] prepend(Object first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private long elapsedMillis(long startedAt) {
        return nanosToMillis(System.nanoTime() - startedAt);
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
