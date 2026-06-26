package com.loopers.config;

import com.loopers.domain.like.LikeSort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LikeSortConverter implements Converter<String, LikeSort> {

    @Override
    public LikeSort convert(String value) {
        return LikeSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
