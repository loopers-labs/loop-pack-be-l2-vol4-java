package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandFinder {

    private final BrandRepository brandRepository;

    public BrandModel getById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    public Map<Long, BrandModel> getMapByIds(Collection<Long> ids) {
        return brandRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(BrandModel::getId, b -> b));
    }
}
