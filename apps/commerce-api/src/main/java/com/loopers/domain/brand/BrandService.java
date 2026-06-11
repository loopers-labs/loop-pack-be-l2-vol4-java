package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandModel getById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    public Map<Long, BrandModel> getMapByIds(Collection<Long> ids) {
        return brandRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(BrandModel::getId, b -> b));
    }

    public Page<BrandModel> findAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    public BrandModel create(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    public BrandModel update(Long id, String name) {
        BrandModel brand = getById(id);
        brand.update(name);
        return brandRepository.save(brand);
    }

    public void delete(Long id) {
        BrandModel brand = getById(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
