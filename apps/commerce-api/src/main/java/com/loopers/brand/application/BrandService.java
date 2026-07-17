package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand create(String name, String description) {
        return brandRepository.save(new Brand(name, description));
    }

    public Brand get(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    public List<Brand> getAll() {
        return brandRepository.findAll();
    }

    public Map<Long, Brand> getMapByIds(Collection<Long> ids) {
        return brandRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    public Brand update(Long id, String name, String description) {
        Brand brand = get(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    public void delete(Long id) {
        Brand brand = get(id);
        brand.delete();
        brandRepository.save(brand);
    }

    public void ensureExists(Long id) {
        if (!brandRepository.existsById(id)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다.");
        }
    }
}
