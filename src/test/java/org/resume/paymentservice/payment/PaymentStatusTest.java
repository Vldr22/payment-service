package org.resume.paymentservice.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.resume.paymentservice.model.enums.PaymentStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentStatus - граф допустимых переходов")
class PaymentStatusTest {

    /**
     * Из рабочих статусов Stripe может вернуть платёж в любой другой рабочий или финальный,
     * кроме возврата: он делается только с успешного платежа.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDING, PROCESSING",
            "PENDING, SUCCEEDED",
            "PENDING, FAILED",
            "PENDING, CANCELED",

            "PROCESSING, PENDING",
            "PROCESSING, SUCCEEDED",
            "PROCESSING, FAILED",
            "PROCESSING, CANCELED",

            "FAILED, PENDING",
            "FAILED, PROCESSING",
            "FAILED, SUCCEEDED",
            "FAILED, CANCELED",

            "SUCCEEDED, REFUNDED"
    })
    void shouldAllowTransition(PaymentStatus from, PaymentStatus target) {
        assertThat(from.isPossibleTransitionTo(target)).isTrue();
    }

    /**
     * Возврат недостижим из рабочих статусов, успешный платёж не откатывается назад,
     * отменённый и возвращённый - финальные. Переход в себя переходом не считается.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDING, PENDING",
            "PROCESSING, PROCESSING",
            "FAILED, FAILED",

            "PENDING, REFUNDED",
            "PROCESSING, REFUNDED",
            "FAILED, REFUNDED",

            "SUCCEEDED, PENDING",
            "SUCCEEDED, PROCESSING",
            "SUCCEEDED, SUCCEEDED",
            "SUCCEEDED, FAILED",
            "SUCCEEDED, CANCELED",

            "CANCELED, PENDING",
            "CANCELED, PROCESSING",
            "CANCELED, SUCCEEDED",
            "CANCELED, FAILED",
            "CANCELED, CANCELED",
            "CANCELED, REFUNDED",

            "REFUNDED, PENDING",
            "REFUNDED, PROCESSING",
            "REFUNDED, SUCCEEDED",
            "REFUNDED, FAILED",
            "REFUNDED, CANCELED",
            "REFUNDED, REFUNDED"
    })
    void shouldRejectTransition(PaymentStatus from, PaymentStatus target) {
        assertThat(from.isPossibleTransitionTo(target)).isFalse();
    }

}
