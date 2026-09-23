package org.resume.paymentservice.payment;

import com.stripe.model.PaymentIntent;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.dto.data.PaymentCreationData;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.SavedCard;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.model.enums.PaymentStatus;
import org.resume.paymentservice.repository.PaymentRepository;
import org.resume.paymentservice.service.payment.PaymentService;
import org.resume.paymentservice.service.user.UserService;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — операции с платежами в БД")
class PaymentServiceTest {

    private static final String EXISTING_STRIPE_ID = "pi_existing_123";
    private static final String NONEXISTENT_STRIPE_ID = "pi_nonexistent_999";
    private static final String BILLING_STRIPE_ID = "pi_billing_456";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private PaymentService paymentService;

    private User owner;
    private User anotherUser;
    private Payment payment;

    @BeforeEach
    void setUp() {
        owner = Instancio.of(User.class)
                .set(field(User::getId), 1L)
                .create();

        anotherUser = Instancio.of(User.class)
                .set(field(User::getId), 2L)
                .create();

        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), owner)
                .set(field(Payment::getStripePaymentIntentId), EXISTING_STRIPE_ID)
                .create();
    }

    // savePayment

    /**
     * Проверяет, что savePayment создаёт Payment со статусом PENDING
     * и корректно маппит все поля из PaymentCreationData.
     */
    @Test
    void shouldSavePayment_withPendingStatusAndCorrectFields() {
        PaymentCreationData data = Instancio.of(PaymentCreationData.class)
                .set(field(PaymentCreationData::getUserId), owner.getId())
                .create();

        when(userService.getUserById(owner.getId())).thenReturn(owner);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.savePayment(data);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getUser()).isEqualTo(owner);
        assertThat(result.getStripePaymentIntentId()).isEqualTo(data.getStripePaymentIntentId());
        assertThat(result.getAmount()).isEqualTo(data.getAmount());
        verify(paymentRepository).save(any(Payment.class));
    }

    // saveBillingPayment

    /**
     * Проверяет, что saveBillingPayment сохраняет биллинговый платёж
     * со статусом PENDING, привязанными картой и пользователем из подписки.
     */
    @Test
    void shouldSaveBillingPayment_withPendingStatusAndLinkedCard() {
        Subscription subscription = Instancio.of(Subscription.class)
                .set(field(Subscription::getUser), owner)
                .create();

        PaymentIntent paymentIntent = buildPaymentIntent(BILLING_STRIPE_ID);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.saveBillingPayment(paymentIntent, subscription);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getUser()).isEqualTo(owner);
        assertThat(result.getSavedCard()).isEqualTo(subscription.getSavedCard());
        assertThat(result.getStripePaymentIntentId()).isEqualTo(BILLING_STRIPE_ID);
    }

    // findByStripePaymentIntentId

    /**
     * Проверяет, что метод возвращает платёж по-существующему Stripe ID.
     */
    @Test
    void shouldReturnPayment_whenStripeIdExists() {
        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.findByStripePaymentIntentId(EXISTING_STRIPE_ID);

        assertThat(result).isEqualTo(payment);
    }

    /**
     * Проверяет, что метод бросает NotFoundException
     * когда платёж с указанным Stripe ID не существует в системе.
     */
    @Test
    void shouldThrowNotFound_whenStripeIdNotExist() {
        when(paymentRepository.findByStripePaymentIntentId(NONEXISTENT_STRIPE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findByStripePaymentIntentId(NONEXISTENT_STRIPE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // findByStripePaymentIntentIdAndUser

    /**
     * Проверяет, что метод возвращает платёж
     * когда запрашивающий пользователь является владельцем платежа.
     */
    @Test
    void shouldReturnPayment_whenRequesterIsOwner() {
        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.findByStripePaymentIntentIdAndUser(EXISTING_STRIPE_ID, owner);

        assertThat(result).isEqualTo(payment);
    }

    /**
     * Проверяет, что метод бросает AccessDeniedException
     * когда платёж принадлежит другому пользователю (защита от доступа к чужим данным).
     */
    @Test
    void shouldThrowAccessDenied_whenRequesterIsNotOwner() {
        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                paymentService.findByStripePaymentIntentIdAndUser(EXISTING_STRIPE_ID, anotherUser)
        ).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Проверяет, что метод бросает NotFoundException
     * когда платёж с указанным Stripe ID отсутствует в системе.
     */
    @Test
    void shouldThrowNotFound_whenPaymentDoesNotExist() {
        when(paymentRepository.findByStripePaymentIntentId(NONEXISTENT_STRIPE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.findByStripePaymentIntentIdAndUser(NONEXISTENT_STRIPE_ID, owner)
        ).isInstanceOf(NotFoundException.class);
    }

    // updatePaymentStatus

    /**
     * Проверяет, что статус платежа обновляется и платёж сохраняется в БД
     * когда новый статус отличается от текущего.
     */
    @Test
    void shouldUpdateStatus_whenStatusChanged() {
        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), owner)
                .set(field(Payment::getStripePaymentIntentId), EXISTING_STRIPE_ID)
                .set(field(Payment::getStatus), PaymentStatus.PENDING)
                .create();

        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        paymentService.updatePaymentStatus(EXISTING_STRIPE_ID, PaymentStatus.SUCCEEDED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(payment);
    }

    /**
     * Проверяет, что save не вызывается когда новый статус совпадает с текущим (оптимизация лишних запросов в БД).
     */
    @Test
    void shouldSkipSave_whenStatusNotChanged() {
        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), owner)
                .set(field(Payment::getStripePaymentIntentId), EXISTING_STRIPE_ID)
                .set(field(Payment::getStatus), PaymentStatus.PENDING)
                .create();

        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        paymentService.updatePaymentStatus(EXISTING_STRIPE_ID, PaymentStatus.PENDING);

        verify(paymentRepository, never()).save(any());
    }

    /**
     * Проверяет, что опоздавшее событие processing не откатывает успешный платёж.
     */
    @Test
    void shouldSkipSave_whenTransitionNotAllowed() {
        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), owner)
                .set(field(Payment::getStripePaymentIntentId), EXISTING_STRIPE_ID)
                .set(field(Payment::getStatus), PaymentStatus.SUCCEEDED)
                .create();

        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        paymentService.updatePaymentStatus(EXISTING_STRIPE_ID, PaymentStatus.PROCESSING);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository, never()).save(any());
    }

    /**
     * Проверяет, что возвращённый платёж не становится успешным повторно:
     * PaymentIntent в Stripe после возврата остаётся succeeded.
     */
    @Test
    void shouldSkipSave_whenPaymentRefunded() {
        payment = Instancio.of(Payment.class)
                .set(field(Payment::getUser), owner)
                .set(field(Payment::getStripePaymentIntentId), EXISTING_STRIPE_ID)
                .set(field(Payment::getStatus), PaymentStatus.REFUNDED)
                .create();

        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        paymentService.updatePaymentStatus(EXISTING_STRIPE_ID, PaymentStatus.SUCCEEDED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository, never()).save(any());
    }

    // updateSavedCard

    /**
     * Проверяет, что карта привязывается к платежу и обновлённый платёж сохраняется в БД.
     */
    @Test
    void shouldAttachCardToPayment() {
        SavedCard card = Instancio.create(SavedCard.class);

        when(paymentRepository.findByStripePaymentIntentId(EXISTING_STRIPE_ID))
                .thenReturn(Optional.of(payment));

        paymentService.updateSavedCard(EXISTING_STRIPE_ID, card);

        assertThat(payment.getSavedCard()).isEqualTo(card);
        verify(paymentRepository).save(payment);
    }

    // Фабричный метод для создания тестовых объектов

    /**
     * Создаёт замоканный PaymentIntent с заданным Stripe ID.
     */
    private PaymentIntent buildPaymentIntent(String stripeId) {
        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn(stripeId);
        return paymentIntent;
    }
}