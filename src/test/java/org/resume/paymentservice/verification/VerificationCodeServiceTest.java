package org.resume.paymentservice.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.resume.paymentservice.exception.VerificationException;
import org.resume.paymentservice.properties.VerificationProperties;
import org.resume.paymentservice.service.verification.SmsSender;
import org.resume.paymentservice.service.verification.VerificationCodeService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeService - выдача и проверка кодов верификации")
class VerificationCodeServiceTest {

    private static final String PHONE = "+79001234567";
    private static final String VALID_CODE = "123456";
    private static final String BUCKET_KEY = "verification:" + PHONE;
    private static final String ATTEMPTS_KEY = "verification:attempts:" + PHONE;

    private static final int CODE_TTL_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCKOUT_MINUTES = 15;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private RAtomicLong attempts;

    @Mock
    private SmsSender smsSender;

    private VerificationCodeService verificationCodeService;

    @BeforeEach
    void setUp() {
        VerificationProperties properties = new VerificationProperties(
                CODE_TTL_SECONDS, MAX_ATTEMPTS, LOCKOUT_MINUTES);

        verificationCodeService = new VerificationCodeService(redissonClient, properties, smsSender);

        when(redissonClient.getAtomicLong(ATTEMPTS_KEY)).thenReturn(attempts);
    }

    // sendCode

    /**
     * Проверяет что код сохраняется в Redis с настроенным TTL.
     */
    @Test
    void shouldSendCode_andStoreInRedisWithTtl() {
        givenBucket();

        verificationCodeService.sendCode(PHONE);

        verify(bucket).set(anyString(), eq(Duration.ofSeconds(CODE_TTL_SECONDS)));
    }

    /**
     * Проверяет что код уходит в канал доставки, а не остаётся только в Redis.
     */
    @Test
    void shouldPassCodeToSmsSender() {
        givenBucket();

        verificationCodeService.sendCode(PHONE);

        verify(smsSender).send(eq(PHONE), anyString());
    }

    /**
     * Новый код не выдаётся, пока номер заблокирован после исчерпания попыток.
     */
    @Test
    void shouldRefuseNewCode_whenAttemptsExceeded() {
        when(attempts.get()).thenReturn((long) MAX_ATTEMPTS);

        assertThatThrownBy(() -> verificationCodeService.sendCode(PHONE))
                .isInstanceOf(VerificationException.class);

        verify(smsSender, never()).send(anyString(), anyString());
    }

    // verifyCode

    /**
     * Проверяет успешную верификацию: код и счётчик промахов удаляются.
     */
    @Test
    void shouldVerifyCode_andClearRedis() {
        givenBucket();
        when(bucket.get()).thenReturn(VALID_CODE);

        verificationCodeService.verifyCode(PHONE, VALID_CODE);

        verify(bucket).delete();
        verify(attempts).delete();
    }

    /**
     * Бросает VerificationException если код в Redis истёк или не существует.
     */
    @Test
    void shouldThrowVerificationException_whenCodeExpired() {
        givenBucket();
        when(bucket.get()).thenReturn(null);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(PHONE, VALID_CODE))
                .isInstanceOf(VerificationException.class);
    }

    /**
     * Промах увеличивает счётчик и продлевает окно блокировки.
     */
    @Test
    void shouldCountFailedAttempt_whenCodeInvalid() {
        givenBucket();
        when(bucket.get()).thenReturn("999999");

        assertThatThrownBy(() -> verificationCodeService.verifyCode(PHONE, VALID_CODE))
                .isInstanceOf(VerificationException.class);

        verify(attempts).incrementAndGet();
        verify(attempts).expire(Duration.ofMinutes(LOCKOUT_MINUTES));
    }

    /**
     * Последний промах гасит код, чтобы новым запросом его нельзя было добрать.
     */
    @Test
    void shouldDeleteCode_whenLastAttemptFailed() {
        givenBucket();
        when(bucket.get()).thenReturn("999999");
        when(attempts.incrementAndGet()).thenReturn((long) MAX_ATTEMPTS);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(PHONE, VALID_CODE))
                .isInstanceOf(VerificationException.class);

        verify(bucket).delete();
    }

    /**
     * Проверка кода недоступна, пока номер заблокирован.
     */
    @Test
    void shouldRefuseVerification_whenAttemptsExceeded() {
        when(attempts.get()).thenReturn((long) MAX_ATTEMPTS);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(PHONE, VALID_CODE))
                .isInstanceOf(VerificationException.class);

        verify(bucket, never()).get();
    }

    private void givenBucket() {
        when(redissonClient.<String>getBucket(BUCKET_KEY)).thenReturn(bucket);
    }

}
