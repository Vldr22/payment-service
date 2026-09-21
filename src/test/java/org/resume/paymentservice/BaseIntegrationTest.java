package org.resume.paymentservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.stripe.Stripe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@SuppressWarnings("resource")
@ImportAutoConfiguration(exclude = {RedissonAutoConfiguration.class})
public abstract class BaseIntegrationTest {

    private static final int WIREMOCK_PORT = 9999;

    @MockitoBean
    private RedissonClient redissonClient;

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("payment_service")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
    }

    protected static WireMockServer wireMock = new WireMockServer(
            WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT)
    );

    @BeforeAll
    static void startWireMock() {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }

        Stripe.overrideApiBase("http://localhost:" + WIREMOCK_PORT);
        Stripe.apiKey = "sk_test_fake";
    }

    @BeforeEach
    void configureWireMock() {
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterAll
    static void stopWireMock() {
    }

    @AfterEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}