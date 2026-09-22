package org.resume.paymentservice.webhook.handler;

import com.stripe.Stripe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.resume.paymentservice.BaseIntegrationTest;
import org.resume.paymentservice.exception.WebhookProcessingException;
import org.resume.paymentservice.model.entity.*;
import org.resume.paymentservice.model.enums.*;
import org.resume.paymentservice.repository.*;
import org.resume.paymentservice.service.webhook.WebhookService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentWebhookIT extends BaseIntegrationTest {

    private static final String STRIPE_PAYMENT_ID = "pi_3T7PULRqnwyBFap61fkmmSCw";
    private static final String DEV_SIGNATURE = "dev-skip-verification";
    private static final String API_VERSION_PLACEHOLDER = "{API_VERSION}";

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private SavedCardRepository savedCardRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingAttemptRepository billingAttemptRepository;

    @Autowired
    private RefundRepository refundRepository;

    private User user;
    private Payment payment;

    @BeforeEach
    void setUp() {
        user = new User("Иван", "Иванов", "Иванович", "+79001234567");
        user.setRole(Roles.ROLE_USER);
        userRepository.save(user);

        payment = new Payment();
        payment.setStripePaymentIntentId(STRIPE_PAYMENT_ID);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("9.99"));
        payment.setCurrency(Currency.USD);
        payment.setDescription("Test payment");
        payment.setUser(user);
        paymentRepository.save(payment);
    }

    @AfterEach
    void cleanUp() {
        refundRepository.deleteAll();
        webhookEventRepository.deleteAll();
        billingAttemptRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        savedCardRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ===== payment_intent.succeeded =====

    /**
     * Обычный платёж: webhook payload десериализуется, сохраняется в БД
     * и статус платежа обновляется до SUCCEEDED.
     */
    @Test
    void shouldUpdatePaymentStatus_whenPaymentSucceededWebhookReceived() throws Exception {
        String payload = loadJson("stripe-events/payment_intent_succeeded.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updated = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    /**
     * Биллинг succeeded: webhook с метадатой billing обновляет статус платежа
     * до SUCCEEDED и помечает попытку биллинга успешной.
     */
    @Test
    void shouldHandleBillingSucceeded_whenBillingWebhookReceived() throws Exception {
        SavedCard savedCard = buildSavedCard();
        Subscription subscription = buildSubscription(savedCard);
        buildBillingAttempt(subscription);

        String payload = loadJson("stripe-events/payment_intent_succeeded_billing.json")
                .replace("{SUBSCRIPTION_ID}", String.valueOf(subscription.getId()));

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updatedPayment = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        BillingAttempt updatedAttempt = billingAttemptRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();

        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(updatedAttempt.getStatus()).isEqualTo(BillingAttemptStatus.SUCCEEDED);
    }

    // ===== payment_intent.payment_failed =====

    /**
     * Обычный платёж: статус обновляется до FAILED.
     */
    @Test
    void shouldUpdatePaymentStatus_whenPaymentFailedWebhookReceived() throws Exception {
        String payload = loadJson("stripe-events/payment_intent_failed.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updated = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    /**
     * Биллинг failed: статус платежа обновляется до FAILED,
     * попытка биллинга помечается неудачной.
     */
    @Test
    void shouldHandleBillingFailed_whenBillingFailedWebhookReceived() throws Exception {
        SavedCard savedCard = buildSavedCard();
        Subscription subscription = buildSubscription(savedCard);
        buildBillingAttempt(subscription);

        String payload = loadJson("stripe-events/payment_intent_failed_billing.json")
                .replace("{SUBSCRIPTION_ID}", String.valueOf(subscription.getId()));

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updatedPayment = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        BillingAttempt updatedAttempt = billingAttemptRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();

        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedAttempt.getStatus()).isEqualTo(BillingAttemptStatus.FAILED);
    }

    // ===== payment_intent.processing =====

    /**
     * Платёж в обработке: статус обновляется до PROCESSING.
     */
    @Test
    void shouldUpdatePaymentStatus_whenPaymentProcessingWebhookReceived() throws Exception {
        String payload = loadJson("stripe-events/payment_intent_processing.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updated = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    // ===== payment_intent.canceled =====

    /**
     * Платёж отменён: статус обновляется до CANCELED.
     */
    @Test
    void shouldUpdatePaymentStatus_whenPaymentCanceledWebhookReceived() throws Exception {
        String payload = loadJson("stripe-events/payment_intent_canceled.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updated = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    // ===== Дубликат =====

    /**
     * Повторная обработка того же события отклоняется — выбрасывается
     * WebhookProcessingException.
     */
    @Test
    void shouldRejectDuplicateWebhookEvent() throws Exception {
        String payload = loadJson("stripe-events/payment_intent_succeeded.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        assertThatThrownBy(() -> webhookService.createWebhookEvent(payload, DEV_SIGNATURE))
                .isInstanceOf(WebhookProcessingException.class);
    }

    // ===== Неподдерживаемый тип =====

    /**
     * Неподдерживаемый тип события игнорируется — ничего не сохраняется в БД.
     */
    @Test
    void shouldIgnoreUnsupportedEventType() throws Exception {
        String unsupportedType = "\"payment_intent.unknown_type\"";
        String unsupportedEventId = "\"evt_unsupported001\"";

        String payload = loadJson("stripe-events/payment_intent_succeeded.json")
                .replace("\"payment_intent.succeeded\"", unsupportedType)
                .replace("\"evt_3T7PULRqnwyBFap61qfdBLPf\"", unsupportedEventId);

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        assertThat(webhookEventRepository.count()).isZero();
    }

    // ===== charge.refunded / refund.failed =====

    /**
     * Возврат средств выполнен: статус платежа обновляется до REFUNDED,
     * статус возврата обновляется до SUCCEEDED.
     */
    @Test
    void shouldUpdatePaymentStatus_whenRefundSucceededWebhookReceived() throws Exception {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        buildRefund();

        String payload = loadJson("stripe-events/refund_succeeded.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Payment updatedPayment = paymentRepository.findByStripePaymentIntentId(STRIPE_PAYMENT_ID).orElseThrow();
        Refund updatedRefund = refundRepository.findByPaymentStripePaymentIntentIdAndStatus(
                STRIPE_PAYMENT_ID, RefundStatus.SUCCEEDED).orElseThrow();

        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(updatedRefund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    /**
     * Возврат средств не выполнен: статус возврата обновляется до FAILED.
     */
    @Test
    void shouldUpdateRefundStatus_whenRefundFailedWebhookReceived() throws Exception {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        buildRefund();

        String payload = loadJson("stripe-events/refund_failed.json");

        webhookService.createWebhookEvent(payload, DEV_SIGNATURE);

        Refund updatedRefund = refundRepository.findByPaymentStripePaymentIntentIdAndStatus(
                STRIPE_PAYMENT_ID, RefundStatus.FAILED).orElseThrow();

        assertThat(updatedRefund.getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    // ===== Helpers =====

    /**
     * Создаёт и сохраняет тестовую карту для указанного пользователя.
     */
    private SavedCard buildSavedCard() {
        SavedCard savedCard = new SavedCard();
        savedCard.setStripePaymentMethodId("pm_test_123");
        savedCard.setLast4("4242");
        savedCard.setBrand("visa");
        savedCard.setExpMonth((short) 12);
        savedCard.setExpYear((short) 26);
        savedCard.setUser(user);
        return savedCardRepository.save(savedCard);
    }

    /**
     * Создаёт и сохраняет тестовую подписку типа BASIC для указанной карты.
     */
    private Subscription buildSubscription(SavedCard savedCard) {
        Subscription subscription = new Subscription(
                user, savedCard, SubscriptionType.BASIC,
                new BigDecimal("9.99"), Currency.USD, 30
        );
        return subscriptionRepository.save(subscription);
    }

    /**
     * Создаёт и сохраняет тестовый возврат со статусом APPROVED для текущего платежа.
     */
    private void buildRefund() {
        Refund refund = new Refund(payment, user, RefundReason.DUPLICATE);
        refund.setStatus(RefundStatus.APPROVED);
        refundRepository.save(refund);
    }

    /**
     * Создаёт и сохраняет первую попытку биллинга с привязкой к тестовому payment intent.
     */
    private void buildBillingAttempt(Subscription subscription) {
        BillingAttempt attempt = new BillingAttempt(subscription, 1);
        attempt.setStripePaymentIntentId(STRIPE_PAYMENT_ID);
        billingAttemptRepository.save(attempt);
    }

    /**
     * Загружает JSON файл из src/test/resources по указанному пути.
     */
    /**
     * Загружает фикстуру, подставляя версию API, на которую собран SDK:
     * Stripe не десериализует событие чужой версии.
     */
    private String loadJson(String path) throws Exception {
        String json = new String(Files.readAllBytes(
                Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource(path)).toURI())
        ));
        return json.replace(API_VERSION_PLACEHOLDER, Stripe.API_VERSION);
    }
}
