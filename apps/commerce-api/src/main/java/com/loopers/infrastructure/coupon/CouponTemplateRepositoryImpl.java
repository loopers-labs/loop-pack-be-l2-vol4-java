package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository jpaRepository;

    @Override
    public CouponTemplate save(CouponTemplate template) {
        return jpaRepository.save(template);
    }

    @Override
    public Optional<CouponTemplate> find(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<CouponTemplate> findAll(int page, int size) {
        return jpaRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }
}
