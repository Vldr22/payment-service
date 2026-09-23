package org.resume.paymentservice.service.verification;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.resume.paymentservice.exception.VerificationException;
import org.resume.paymentservice.properties.VerificationProperties;
import org.resume.paymentservice.utils.CodeGenerator;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static org.resume.paymentservice.contants.VerificationConstants.*;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final RedissonClient redissonClient;
    private final VerificationProperties verificationProperties;
    private final SmsSender smsSender;

    /**
     * Выдаёт новый код и отправляет его получателю.
     */
    public void sendCode(String recipient) {
        requireNotLocked(recipient);

        String code = CodeGenerator.generateVerificationCode();
        getCodeBucket(recipient).set(code, Duration.ofSeconds(verificationProperties.getCodeTtlSeconds()));

        smsSender.send(recipient, String.format(CODE_MESSAGE_FORMAT, code));
    }

    /**
     * Проверяет код и гасит его после успеха или исчерпания попыток.
     */
    public void verifyCode(String recipient, String code) {
        requireNotLocked(recipient);

        RBucket<String> bucket = getCodeBucket(recipient);
        String storedCode = bucket.get();

        if (storedCode == null) {
            throw VerificationException.smsCodeExpired(recipient);
        }

        if (!storedCode.equals(code)) {
            registerFailedAttempt(recipient);
            throw VerificationException.smsCodeInvalid(recipient);
        }

        bucket.delete();
        getAttemptsCounter(recipient).delete();
    }

    private void requireNotLocked(String recipient) {
        if (getAttemptsCounter(recipient).get() >= verificationProperties.getMaxAttempts()) {
            throw VerificationException.smsCodeAttemptsExceeded(recipient);
        }
    }

    private void registerFailedAttempt(String recipient) {
        RAtomicLong attempts = getAttemptsCounter(recipient);
        long failed = attempts.incrementAndGet();
        attempts.expire(Duration.ofMinutes(verificationProperties.getLockoutMinutes()));

        if (failed >= verificationProperties.getMaxAttempts()) {
            getCodeBucket(recipient).delete();
        }
    }

    private RBucket<String> getCodeBucket(String recipient) {
        return redissonClient.getBucket(String.format("%s%s", CODE_PREFIX, recipient));
    }

    private RAtomicLong getAttemptsCounter(String recipient) {
        return redissonClient.getAtomicLong(String.format("%s%s", ATTEMPTS_PREFIX, recipient));
    }

}
