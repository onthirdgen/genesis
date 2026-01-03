package com.callaudit.voc.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JPA Converter for List<String> to PostgreSQL text[] array.
 */
@Converter
public class StringArrayConverter implements AttributeConverter<List<String>, Object> {

    @Override
    public Object convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return new String[0];
        }
        return attribute.toArray(new String[0]);
    }

    @Override
    public List<String> convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return Collections.emptyList();
        }
        if (dbData instanceof String[]) {
            return Arrays.asList((String[]) dbData);
        }
        if (dbData instanceof Array) {
            try {
                Object array = ((Array) dbData).getArray();
                if (array instanceof String[]) {
                    return Arrays.asList((String[]) array);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error converting PostgreSQL array to list", e);
            }
        }
        return Collections.emptyList();
    }
}
