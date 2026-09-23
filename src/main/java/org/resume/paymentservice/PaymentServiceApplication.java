package org.resume.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    /*
    stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe

    pm_card_visa                            — Visa, успех
    pm_card_mastercard                      — Mastercard, успех
    pm_card_amex                            — American Express, успех

    pm_card_visa_chargeDeclined             — отклонена
    pm_card_chargeDeclinedInsufficientFunds — недостаточно средств
    pm_card_chargeDeclinedExpiredCard       — карта истекла
    pm_card_chargeDeclinedProcessingError   — ошибка процессинга

    pm_card_threeDSecure2Required           — требует 3DS подтверждение

    */

}
