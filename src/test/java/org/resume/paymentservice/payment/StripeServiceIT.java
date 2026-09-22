package org.resume.paymentservice.payment;

import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.resume.paymentservice.BaseIntegrationTest;
import org.resume.paymentservice.exception.StripePaymentException;
import org.resume.paymentservice.model.dto.data.BillingChargeData;
import org.resume.paymentservice.model.dto.data.SavedCardData;
import org.resume.paymentservice.model.dto.request.CreatePaymentRequest;
import org.resume.paymentservice.model.dto.response.PaymentResponse;
import org.resume.paymentservice.model.enums.Currency;
import org.resume.paymentservice.model.enums.RefundReason;
import org.resume.paymentservice.service.payment.StripeService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeServiceIT extends BaseIntegrationTest {

    private static final String PATH_PAYMENT_INTENTS        = "/v1/payment_intents";
    private static final String PATH_PAYMENT_INTENT_BY_ID   = "/v1/payment_intents/pi_test_001";
    private static final String PATH_PAYMENT_INTENT_CONFIRM = "/v1/payment_intents/pi_test_001/confirm";
    private static final String PATH_REFUNDS                = "/v1/refunds";
    private static final String PATH_CUSTOMERS              = "/v1/customers";
    private static final String PATH_PAYMENT_METHOD_BY_ID   = "/v1/payment_methods/pm_test_001";
    private static final String PATH_PAYMENT_METHOD_ATTACH  = "/v1/payment_methods/pm_test_001/attach";
    private static final String PATH_PAYMENT_METHOD_DETACH  = "/v1/payment_methods/pm_test_001/detach";

    private static final String PAYMENT_INTENT_ID = "pi_test_001";
    private static final String PAYMENT_METHOD_ID = "pm_test_001";
    private static final String CUSTOMER_ID       = "cus_test_001";
    private static final String BILLING_INTENT_ID = "pi_billing_001";

    @Autowired
    private StripeService stripeService;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    // ===== createStripePayment =====

    /**
     * Успешное создание PaymentIntent — возвращает PaymentResponse с корректными полями.
     */
    @Test
    void shouldCreateStripePayment_whenStripeRespondsSuccessfully() {
        StubFactory.stubPost(PATH_PAYMENT_INTENTS, 200, "stripe/payment_intent/created.json");

        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("9.99"), Currency.USD, "https://example.com/return", "Test payment"
        );
        PaymentResponse response = stripeService.createStripePayment(request, CUSTOMER_ID);

        assertThat(response.getId()).isEqualTo(PAYMENT_INTENT_ID);
        assertThat(response.getStatus()).isEqualTo("requires_payment_method");
        assertThat(response.getAmount()).isEqualTo(999L);
        assertThat(response.getCurrency()).isEqualTo("usd");
        assertThat(response.getClientSecret()).isEqualTo("pi_test_001_secret");
    }

    /**
     * Ошибка Stripe при создании платежа — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenCreatePaymentFails() {
        StubFactory.stubPost(PATH_PAYMENT_INTENTS, 402, "stripe/error/card_declined.json");

        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("9.99"), Currency.USD, "https://example.com/return", "Test payment"
        );

        assertThatThrownBy(() -> stripeService.createStripePayment(request, CUSTOMER_ID))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== createRefund =====

    /**
     * Успешное создание возврата — возвращает stripeRefundId.
     */
    @Test
    void shouldCreateRefund_whenStripeRespondsSuccessfully() {
        StubFactory.stubPost(PATH_REFUNDS, 200, "stripe/refund/succeeded.json");

        String refundId = stripeService.createRefund(PAYMENT_INTENT_ID, RefundReason.DUPLICATE);

        assertThat(refundId).isEqualTo("re_test_001");
    }

    /**
     * Ошибка Stripe при создании возврата — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenCreateRefundFails() {
        StubFactory.stubPost(PATH_REFUNDS, 400, "stripe/error/already_refunded.json");

        assertThatThrownBy(() -> stripeService.createRefund(PAYMENT_INTENT_ID, RefundReason.DUPLICATE))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== createCustomer =====

    /**
     * Успешное создание клиента в Stripe — возвращает customerId.
     */
    @Test
    void shouldCreateCustomer_whenStripeRespondsSuccessfully() {
        StubFactory.stubPost(PATH_CUSTOMERS, 200, "stripe/customer/created.json");

        String customerId = stripeService.createCustomer("Иван Иванов", "+79001234567");

        assertThat(customerId).isEqualTo(CUSTOMER_ID);
    }

    /**
     * Ошибка Stripe при создании клиента — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenCreateCustomerFails() {
        StubFactory.stubPost(PATH_CUSTOMERS, 500, "stripe/error/api_error.json");

        assertThatThrownBy(() -> stripeService.createCustomer("Иван Иванов", "+79001234567"))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== addPaymentMethod =====

    /**
     * Успешная привязка платёжного метода — возвращает SavedCardData с корректными полями.
     */
    @Test
    void shouldAddPaymentMethod_whenStripeRespondsSuccessfully() {
        StubFactory.stubGet(PATH_PAYMENT_METHOD_BY_ID,   200, "stripe/payment_method/card.json");
        StubFactory.stubPost(PATH_PAYMENT_METHOD_ATTACH, 200, "stripe/payment_method/card.json");

        SavedCardData result = stripeService.addPaymentMethod(CUSTOMER_ID, PAYMENT_METHOD_ID);

        assertThat(result.stripePaymentMethodId()).isEqualTo(PAYMENT_METHOD_ID);
        assertThat(result.last4()).isEqualTo("4242");
        assertThat(result.brand()).isEqualTo("visa");
        assertThat(result.expMonth()).isEqualTo((short) 12);
        assertThat(result.expYear()).isEqualTo((short) 2027);
    }

    /**
     * Ошибка Stripe при привязке платёжного метода — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenAddPaymentMethodFails() {
        StubFactory.stubGet(PATH_PAYMENT_METHOD_BY_ID, 404, "stripe/error/not_found_payment_method.json");

        assertThatThrownBy(() -> stripeService.addPaymentMethod(CUSTOMER_ID, PAYMENT_METHOD_ID))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== removePaymentMethod =====

    /**
     * Успешное открепление платёжного метода — метод завершается без исключений.
     */
    @Test
    void shouldRemovePaymentMethod_whenStripeRespondsSuccessfully() {
        StubFactory.stubGet(PATH_PAYMENT_METHOD_BY_ID,   200, "stripe/payment_method/card.json");
        StubFactory.stubPost(PATH_PAYMENT_METHOD_DETACH, 200, "stripe/payment_method/card.json");

        stripeService.removePaymentMethod(PAYMENT_METHOD_ID);

        verify(postRequestedFor(urlEqualTo(PATH_PAYMENT_METHOD_DETACH)));
    }

    /**
     * Ошибка Stripe при откреплении платёжного метода — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenRemovePaymentMethodFails() {
        StubFactory.stubGet(PATH_PAYMENT_METHOD_BY_ID, 404, "stripe/error/not_found_payment_method.json");

        assertThatThrownBy(() -> stripeService.removePaymentMethod(PAYMENT_METHOD_ID))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== chargeWithSavedCard =====

    /**
     * Успешное списание через сохранённую карту — возвращает PaymentIntent со статусом succeeded.
     */
    @Test
    void shouldChargeWithSavedCard_whenStripeRespondsSuccessfully() {
        StubFactory.stubPost(PATH_PAYMENT_INTENTS, 200, "stripe/payment_intent/billing_succeeded.json");

        PaymentIntent result = stripeService.chargeWithSavedCard(billingCharge());

        assertThat(result.getId()).isEqualTo(BILLING_INTENT_ID);
        assertThat(result.getStatus()).isEqualTo("succeeded");
    }

    /**
     * Ошибка Stripe при списании через сохранённую карту — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenChargeWithSavedCardFails() {
        StubFactory.stubPost(PATH_PAYMENT_INTENTS, 402, "stripe/error/card_declined.json");

        assertThatThrownBy(() -> stripeService.chargeWithSavedCard(billingCharge()))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== getPaymentStatus =====

    /**
     * Успешное получение статуса платежа — возвращает PaymentResponse с корректным статусом.
     */
    @Test
    void shouldGetPaymentStatus_whenStripeRespondsSuccessfully() {
        StubFactory.stubGet(PATH_PAYMENT_INTENT_BY_ID, 200, "stripe/payment_intent/succeeded.json");

        PaymentResponse response = stripeService.getPaymentStatus(PAYMENT_INTENT_ID);

        assertThat(response.getId()).isEqualTo(PAYMENT_INTENT_ID);
        assertThat(response.getStatus()).isEqualTo("succeeded");
    }

    /**
     * Ошибка Stripe при получении статуса — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenGetPaymentStatusFails() {
        StubFactory.stubGet(PATH_PAYMENT_INTENT_BY_ID, 404, "stripe/error/not_found_payment_intent.json");

        assertThatThrownBy(() -> stripeService.getPaymentStatus(PAYMENT_INTENT_ID))
                .isInstanceOf(StripePaymentException.class);
    }

    // ===== confirmPayment =====

    /**
     * Успешное подтверждение платежа — возвращает PaymentResponse со статусом succeeded.
     */
    @Test
    void shouldConfirmPayment_whenStripeRespondsSuccessfully() {
        StubFactory.stubGet(PATH_PAYMENT_INTENT_BY_ID,    200, "stripe/payment_intent/requires_confirmation.json");
        StubFactory.stubPost(PATH_PAYMENT_INTENT_CONFIRM, 200, "stripe/payment_intent/confirmed.json");

        PaymentResponse response = stripeService.confirmPayment(
                PAYMENT_INTENT_ID, PAYMENT_METHOD_ID, "https://example.com/return"
        );

        assertThat(response.getId()).isEqualTo(PAYMENT_INTENT_ID);
        assertThat(response.getStatus()).isEqualTo("succeeded");
    }

    /**
     * Ошибка Stripe при подтверждении платежа — выбрасывается StripePaymentException.
     */
    @Test
    void shouldThrowStripePaymentException_whenConfirmPaymentFails() {
        StubFactory.stubGet(PATH_PAYMENT_INTENT_BY_ID, 404, "stripe/error/not_found_payment_intent.json");

        assertThatThrownBy(() -> stripeService.confirmPayment(
                PAYMENT_INTENT_ID, PAYMENT_METHOD_ID, "https://example.com/return"
        )).isInstanceOf(StripePaymentException.class);
    }

    private BillingChargeData billingCharge() {
        return BillingChargeData.builder()
                .amount(new BigDecimal("9.99"))
                .currency(Currency.USD)
                .customerId(CUSTOMER_ID)
                .paymentMethodId(PAYMENT_METHOD_ID)
                .description("BASIC")
                .subscriptionId(1L)
                .idempotencyKey("sub_1_2026-01-01T00:00")
                .build();
    }
}