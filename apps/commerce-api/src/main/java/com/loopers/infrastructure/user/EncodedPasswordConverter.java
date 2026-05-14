package com.loopers.infrastructure.user;

import com.loopers.domain.user.EncodedPassword;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncodedPasswordConverter implements AttributeConverter<EncodedPassword, String> {

    @Override
    public String convertToDatabaseColumn(EncodedPassword attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public EncodedPassword convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new EncodedPassword(dbData);
    }
}
