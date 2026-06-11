package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    CouponTemplate saveTemplate(CouponTemplate template);
    Optional<CouponTemplate> findTemplateById(Long id);
    Page<CouponTemplate> findAllTemplates(Pageable pageable);
    void deleteTemplate(Long id);

    CouponIssue saveIssue(CouponIssue issue);
    Optional<CouponIssue> findIssueById(Long id);
    Optional<CouponIssue> findIssueByUserIdAndTemplateId(Long userId, Long templateId);
    List<CouponIssue> findAllIssuesByUserId(Long userId);
    Page<CouponIssue> findAllIssuesByTemplateId(Long templateId, Pageable pageable);
}
