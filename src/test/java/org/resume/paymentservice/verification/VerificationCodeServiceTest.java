package org.resume.paymentservice.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.resume.paymentservice.exception.VerificationException;
import org.resume.paymentservice.service.verification.SmsSender;
import org.resume.paymentservice.service.verification.VerificationCodeService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeService — отправка и проверка кодов верификации")
class VerificationCodeServiceTest {

    private static final String PHONE       = "+79001234567";
    private static final String VALID_CODE  = "123456";
    private static final String BUCKET_KEY  = "verification:" + PHONE;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private SmsSender smsSender;

    @InjectMocks
    private VerificationCodeService verificationCodeService;

    @BeforeEach
    void setUp() {
        when(redissonClient.<String>getBucket(BUCKET_KEY)).thenReturn(bucket);
    }

    // sendCode
    /**
     * Проверяет что код сохраняется в Redis с правильным TTL.
     * Конкретное значение кода не проверяем — оно генерируется случайно.
     */
    @Test
    void shouldSendCode_andStoreInRedisWithTtl() {
        verificationCodeService.sendCode(PHONE);

        verify(bucket).set(anyString(), eq(Duration.ofSeconds(300)));
    }

    /**
     * Проверяет что код уходит в канал доставки, а не остаётся только в Redis.
     */
    @Test
    void shouldPassCodeToSmsSender() {
        verificationCodeService.sendCode(PHONE);

        verify(smsSender).send(eq(PHONE), anyString());
    }

    // verifyCode
    /**
     * Проверяет успешную верификацию кода — код совпадает с сохранённым в Redis,
     * после чего bucket очищается.
     */
    @Test
    void shouldVerifyCode_andDeleteFromRedis() {
        when(bucket.get()).thenReturn(VALID_CODE);

        verificationCodeService.verifyCode(PHONE, VALID_CODE);

        verify(bucket).delete();
    }

    /**
     * Бросает VerificationException если код в Redis истёк или не существует.
     */
    @Test
    void shouldThrowVerificationException_whenCodeExpired() {
        when(bucket.get()).thenReturn(null);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(PHONE, VALID_CODE))
                .isInstanceOf(VerificationException.class);
    }

    /**
     * Бросает VerificationException если введённый код не совпадает с сохранённым.
     */
    @Test
    void shouldThrowVerificationException_whenCodeInvalid() {
        when(bucket.get()).thenReturn("999999");

        assertThatThrownBy(() -> verificationCodeService.verifyCode(PHONE, VALID_CODE))
                .isInstanceOf(VerificationException.class);
    }
}
