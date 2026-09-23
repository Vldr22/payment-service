package org.resume.paymentservice.service.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Заглушка вместо SMS-провайдера: печатает сообщение в лог.
 */
@Slf4j
@Component
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phone, String message) {
        log.warn("SMS provider is not configured, message is written to log: phone={}, message={}",
                phone, message);
    }

}
