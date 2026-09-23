package org.resume.paymentservice.service.verification;

/**
 * Канал доставки SMS.
 */
public interface SmsSender {

    void send(String phone, String message);

}
