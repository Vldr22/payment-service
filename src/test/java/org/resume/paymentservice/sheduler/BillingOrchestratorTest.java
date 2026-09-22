package org.resume.paymentservice.sheduler;

import com.stripe.model.PaymentIntent;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.StripePaymentException;
import org.resume.paymentservice.model.dto.data.BillingChargeData;
import org.resume.paymentservice.model.entity.BillingAttempt;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.service.payment.PaymentService;
import org.resume.paymentservice.service.payment.StripeService;
import org.resume.paymentservice.service.sheduler.BillingOrchestrator;
import org.resume.paymentservice.service.subscription.BillingAttemptService;
import org.resume.paymentservice.service.subscription.SubscriptionService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingOrchestrator — списание по подписке")
class BillingOrchestratorTest {

    private static final Long SUBSCRIPTION_ID = 1L;
    private static final String STRIPE_PAYMENT_ID = "pi_billing_001";
    private static final String EXPECTED_IDEMPOTENCY_KEY = "sub_1_2026-01-01T09:00";
    private static final LocalDateTime BILLING_PERIOD = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private BillingAttemptService billingAttemptService;

    @Mock
    private StripeService stripeService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private BillingOrchestrator billingOrchestrator;

    private Subscription subscription;
    private BillingAttempt attempt;
    private PaymentIntent paymentIntent;

    @BeforeEach
    void setUp() {
        subscription = Instancio.of(Subscription.class)
                .set(field(Subscription::getId), SUBSCRIPTION_ID)
                .set(field(Subscription::getNextBillingDate), BILLING_PERIOD)
                .set(field(Subscription::getRetryCount), 0)
                .create();

        attempt = new BillingAttempt(subscription, 1, BILLING_PERIOD);

        paymentIntent = mock(PaymentIntent.class);
    }

    /**
     * Подписка выходит из выборки планировщика до обращения к Stripe:
     * иначе падение после списания приведёт к повторному списанию в следующем прогоне.
     */
    @Test
    void shouldMarkProcessing_beforeCallingStripe() {
        givenAttemptCreated();
        givenChargeSucceeds();

        billingOrchestrator.processSubscription(subscription);

        InOrder inOrder = inOrder(subscriptionService, stripeService);
        inOrder.verify(subscriptionService).markProcessing(subscription);
        inOrder.verify(stripeService).chargeWithSavedCard(any(BillingChargeData.class));
    }

    /**
     * Ключ идемпотентности собирается из подписки и периода: повторный запрос
     * с тем же ключом вернёт созданный ранее PaymentIntent вместо нового списания.
     */
    @Test
    void shouldSendIdempotencyKey_builtFromSubscriptionAndPeriod() {
        givenAttemptCreated();
        givenChargeSucceeds();

        billingOrchestrator.processSubscription(subscription);

        ArgumentCaptor<BillingChargeData> captor = ArgumentCaptor.forClass(BillingChargeData.class);
        verify(stripeService).chargeWithSavedCard(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(EXPECTED_IDEMPOTENCY_KEY);
    }

    /**
     * Успешное списание: попытка получает Stripe ID, платёж сохраняется,
     * подписка не помечается неудачной.
     */
    @Test
    void shouldRecordPayment_whenChargeSucceeds() {
        givenAttemptCreated();
        givenChargeSucceeds();

        billingOrchestrator.processSubscription(subscription);

        assertThat(attempt.getStripePaymentIntentId()).isEqualTo(STRIPE_PAYMENT_ID);
        verify(billingAttemptService).save(attempt);
        verify(paymentService).saveBillingPayment(paymentIntent, subscription);
        verify(subscriptionService, never()).markFailed(any(Subscription.class));
    }

    /**
     * Stripe отказал: попытка и подписка помечаются неудачными, платёж не сохраняется.
     */
    @Test
    void shouldMarkFailed_whenChargeRejectedByStripe() {
        givenAttemptCreated();
        when(stripeService.chargeWithSavedCard(any(BillingChargeData.class)))
                .thenThrow(StripePaymentException.byCreationError("card_declined", new RuntimeException()));

        billingOrchestrator.processSubscription(subscription);

        verify(billingAttemptService).markFailed(eq(attempt), anyString());
        verify(subscriptionService).markFailed(subscription);
        verify(paymentService, never()).saveBillingPayment(any(PaymentIntent.class), any(Subscription.class));
    }

    /**
     * Деньги списаны, но запись в БД не удалась: подписка и попытка не помечаются
     * неудачными, иначе следующий прогон спишет повторно. Исключение наружу не выходит,
     * чтобы не прервать обработку остальных подписок.
     */
    @Test
    void shouldNotMarkFailed_whenRecordingFailsAfterCharge() {
        givenAttemptCreated();
        givenChargeSucceeds();
        when(paymentService.saveBillingPayment(paymentIntent, subscription))
                .thenThrow(new RuntimeException("db is down"));

        assertThatNoException().isThrownBy(() -> billingOrchestrator.processSubscription(subscription));

        verify(subscriptionService, never()).markFailed(any(Subscription.class));
        verify(billingAttemptService, never()).markFailed(any(BillingAttempt.class), anyString());
    }

    /**
     * Номер попытки считается от счётчика ретраев подписки.
     */
    @Test
    void shouldNumberAttempt_fromSubscriptionRetryCount() {
        subscription.setRetryCount(2);
        when(billingAttemptService.findOrCreatePending(subscription, 3, BILLING_PERIOD)).thenReturn(attempt);
        givenChargeSucceeds();

        billingOrchestrator.processSubscription(subscription);

        verify(billingAttemptService).findOrCreatePending(subscription, 3, BILLING_PERIOD);
    }

    private void givenAttemptCreated() {
        when(billingAttemptService.findOrCreatePending(subscription, 1, BILLING_PERIOD)).thenReturn(attempt);
    }

    private void givenChargeSucceeds() {
        when(paymentIntent.getId()).thenReturn(STRIPE_PAYMENT_ID);
        when(stripeService.chargeWithSavedCard(any(BillingChargeData.class))).thenReturn(paymentIntent);
    }
}
