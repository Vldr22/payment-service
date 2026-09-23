package org.resume.paymentservice.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter
public class BigDecimalToLongConverter implements AttributeConverter<BigDecimal, Long> {

    @Override
    public Long convertToDatabaseColumn(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return MoneyUtils.toMinorUnits(amount);
    }

    @Override
    public BigDecimal convertToEntityAttribute(Long dbData) {
        if (dbData == null) {
            return null;
        }
        return MoneyUtils.toMajorUnits(dbData);
    }

}
