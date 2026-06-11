package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponTemplateJpaRepository templateJpaRepository;
    private final CouponIssueJpaRepository issueJpaRepository;

    @Override
    public CouponTemplate saveTemplate(CouponTemplate template) {
        return templateJpaRepository.save(template);
    }

    @Override
    public Optional<CouponTemplate> findTemplateById(Long id) {
        return templateJpaRepository.findById(id);
    }

    @Override
    public Page<CouponTemplate> findAllTemplates(Pageable pageable) {
        return templateJpaRepository.findAll(pageable);
    }

    @Override
    public void deleteTemplate(Long id) {
        templateJpaRepository.deleteById(id);
    }

    @Override
    public CouponIssue saveIssue(CouponIssue issue) {
        return issueJpaRepository.save(issue);
    }

    @Override
    public Optional<CouponIssue> findIssueById(Long id) {
        return issueJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponIssue> findIssueByUserIdAndTemplateId(Long userId, Long templateId) {
        return issueJpaRepository.findByUserIdAndCouponTemplateId(userId, templateId);
    }

    @Override
    public List<CouponIssue> findAllIssuesByUserId(Long userId) {
        return issueJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Page<CouponIssue> findAllIssuesByTemplateId(Long templateId, Pageable pageable) {
        return issueJpaRepository.findAllByCouponTemplateId(templateId, pageable);
    }

    @Override
    public List<CouponTemplate> findTemplatesByIds(List<Long> ids) {
        return templateJpaRepository.findByIdIn(ids);
    }
}
