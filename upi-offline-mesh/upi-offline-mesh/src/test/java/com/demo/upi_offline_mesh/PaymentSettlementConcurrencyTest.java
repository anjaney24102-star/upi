package com.demo.upi_offline_mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.demo.upi_offline_mesh.model.DemoAccount;
import com.demo.upi_offline_mesh.model.PaymentInstruction;
import com.demo.upi_offline_mesh.model.PaymentPacket;
import com.demo.upi_offline_mesh.model.PaymentReceipt;
import com.demo.upi_offline_mesh.repository.DemoAccountRepository;
import com.demo.upi_offline_mesh.repository.PaymentTransactionRepository;
import com.demo.upi_offline_mesh.service.PaymentService;
import com.demo.upi_offline_mesh.service.PinHasher;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class PaymentSettlementConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DemoAccountRepository accountRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private PinHasher pinHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.encryption-key}")
    private String encodedEncryptionKey;

    @BeforeEach
    void resetDemoAccounts() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.save(new DemoAccount("A", new BigDecimal("1000.00"), pinHasher.hash("1234")));
        accountRepository.save(new DemoAccount("B", new BigDecimal("1000.00"), pinHasher.hash("5678")));
    }

    @Test
    void simultaneousUploadsOfSamePacketSettleAndDebitOnlyOnce() throws Exception {
        PaymentPacket packet = paymentService.createPayment("A", "B", new BigDecimal("250.00"), "1234");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger settled = new AtomicInteger();
        AtomicInteger rejectedAsDuplicate = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> uploadAtOnce(packet, ready, start, settled, rejectedAsDuplicate));
            Future<?> second = executor.submit(() -> uploadAtOnce(packet, ready, start, settled, rejectedAsDuplicate));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both uploads should be ready");
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        assertEquals(1, settled.get());
        assertEquals(1, rejectedAsDuplicate.get());
        assertEquals(0, new BigDecimal("750.00").compareTo(accountRepository.findById("A").orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("1250.00").compareTo(accountRepository.findById("B").orElseThrow().getBalance()));
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void tamperedPacketDoesNotChangeBalances() {
        PaymentPacket packet = paymentService.createPayment("A", "B", new BigDecimal("250.00"), "1234");
        String ciphertext = packet.ciphertext();
        char replacement = ciphertext.charAt(0) == 'A' ? 'B' : 'A';
        PaymentPacket tampered = new PaymentPacket(packet.transactionId(), packet.iv(),
            replacement + ciphertext.substring(1));

        assertThrows(ResponseStatusException.class, () -> paymentService.receivePayment(tampered));
        assertEquals(0, new BigDecimal("1000.00").compareTo(accountRepository.findById("A").orElseThrow().getBalance()));
        assertEquals(0, transactionRepository.count());
    }

        @Test
        void expiredPacketIsRejectedBeforeSettlement() throws Exception {
        Instant timestamp = Instant.now().minusSeconds(60);
        PaymentInstruction expired = new PaymentInstruction("TX-EXPIRED", "A", "B",
            new BigDecimal("250.00"), timestamp, timestamp.minusSeconds(30), "nonce", "1234");
        byte[] iv = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
            new SecretKeySpec(Base64.getDecoder().decode(encodedEncryptionKey), "AES"),
            new GCMParameterSpec(128, iv));
        cipher.updateAAD(expired.transactionId().getBytes(StandardCharsets.UTF_8));
        PaymentPacket packet = new PaymentPacket(expired.transactionId(), Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(cipher.doFinal(objectMapper.writeValueAsBytes(expired))));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> paymentService.receivePayment(packet));
        assertEquals(HttpStatus.GONE.value(), exception.getStatusCode().value());
        assertEquals(0, new BigDecimal("1000.00").compareTo(accountRepository.findById("A").orElseThrow().getBalance()));
        assertEquals(0, transactionRepository.count());
        }

    private void uploadAtOnce(PaymentPacket packet, CountDownLatch ready, CountDownLatch start,
                              AtomicInteger settled, AtomicInteger rejectedAsDuplicate) {
        ready.countDown();
        try {
            start.await();
            PaymentReceipt receipt = paymentService.receivePayment(packet);
            if ("SETTLED".equals(receipt.status())) {
                settled.incrementAndGet();
            }
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                rejectedAsDuplicate.incrementAndGet();
            } else {
                throw exception;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}