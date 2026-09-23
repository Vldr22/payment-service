package org.resume.paymentservice.utils;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class MoneyUtils {

    private static final int MINOR_UNIT_SCALE = 2;

    /**
     * Переводит сумму в минорные единицы валюты.
     */
    public static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(MINOR_UNIT_SCALE).longValueExact();
    }

    /**
     * Переводит минорные единицы валюты в сумму.
     */
    public static BigDecimal toMajorUnits(long minorUnits) {
        return BigDecimal.valueOf(minorUnits).movePointLeft(MINOR_UNIT_SCALE);
    }

}
