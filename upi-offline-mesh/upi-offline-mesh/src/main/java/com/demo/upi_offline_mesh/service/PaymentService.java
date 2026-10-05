package com.demo.upi_offline_mesh.service;

import com.demo.upi_offline_mesh.model.DemoAccount;
import com.demo.upi_offline_mesh.model.PaymentInstruction;
import com.demo.upi_offline_mesh.model.PaymentPacket;
import com.demo.upi_offline_mesh.model.PaymentReceipt;
import com.demo.upi_offline_mesh.model.PaymentTransaction;
import com.demo.upi_offline_mesh.repository.DemoAccountRepository;
import com.demo.upi_offline_mesh.repository.PaymentTransactionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int AES_KEY_LENGTH_BYTES = 32;

    private final DemoAccountRepository accountRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentSettlementService settlementService;
    private final PinHasher pinHasher;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String encodedEncryptionKey;
    private final long expirySeconds;

    public PaymentService(
            DemoAccountRepository accountRepository,
            PaymentTransactionRepository transactionRepository,
            PaymentSettlementService settlementService,
            PinHasher pinHasher,
            ObjectMapper objectMapper,
            @Value("${payment.encryption-key}") String encodedEncryptionKey,
            @Value("${payment.expiry-seconds:300}") long expirySeconds) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.settlementService = settlementService;
        this.pinHasher = pinHasher;
        this.objectMapper = objectMapper;
        this.encodedEncryptionKey = encodedEncryptionKey;
        this.expirySeconds = expirySeconds;
    }

    public PaymentPacket createPayment(String senderId, String receiverId, BigDecimal amount, String pin) {
        if (senderId == null || senderId.isBlank() || receiverId == null || receiverId.isBlank()
                || amount == null || amount.signum() <= 0 || pin == null || pin.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender, receiver, positive amount, and PIN are required");
        }
        if (senderId.equals(receiverId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender and receiver must differ");
        }
        if (expirySeconds <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment expiry must be positive");
        }

        DemoAccount sender = accountRepository.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown sender account"));
        if (!pinHasher.matches(pin, sender.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect sender PIN");
        }
        if (!accountRepository.existsById(receiverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown receiver account");
        }

        BigDecimal ledgerAmount;
        try {
            ledgerAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount cannot have more than two decimal places", exception);
        }
        Instant timestamp = Instant.now();
        String transactionId = "TX-" + UUID.randomUUID();
        PaymentInstruction instruction = new PaymentInstruction(
                transactionId, senderId, receiverId, ledgerAmount, timestamp,
                timestamp.plusSeconds(expirySeconds), UUID.randomUUID().toString(), pin);

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(transactionId.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(instruction));
            return new PaymentPacket(transactionId, Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext));
        } catch (GeneralSecurityException | JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encrypt payment", exception);
        }
    }

    public PaymentReceipt receivePayment(PaymentPacket packet) {
        PaymentInstruction instruction = decryptAndVerify(packet);
        if (instruction.expiry() == null || !instruction.expiry().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Payment packet has expired");
        }
        return settlementService.settle(instruction);
    }

    public List<PaymentTransaction> getTransactions() {
        return transactionRepository.findAllByOrderByTimestampDesc();
    }

    public List<AccountView> getAccounts() {
        return accountRepository.findAllByOrderByUserIdAsc().stream()
                .map(account -> new AccountView(account.getUserId(), account.getBalance()))
                .toList();
    }

    private PaymentInstruction decryptAndVerify(PaymentPacket packet) {
        if (packet == null || packet.transactionId() == null || packet.transactionId().isBlank()
                || packet.iv() == null || packet.ciphertext() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment packet is incomplete");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(packet.iv());
            byte[] ciphertext = Base64.getDecoder().decode(packet.ciphertext());
            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid AES-GCM IV length");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(packet.transactionId().getBytes(StandardCharsets.UTF_8));
            PaymentInstruction instruction = objectMapper.readValue(cipher.doFinal(ciphertext), PaymentInstruction.class);
            if (!packet.transactionId().equals(instruction.transactionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction ID verification failed");
            }
            return instruction;
        } catch (GeneralSecurityException | JacksonException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment packet is invalid or tampered", exception);
        }
    }

    private SecretKeySpec encryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(encodedEncryptionKey);
            if (key.length != AES_KEY_LENGTH_BYTES) {
                throw new IllegalArgumentException("Expected a Base64-encoded 256-bit key");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment encryption key is invalid", exception);
        }
    }

    public record AccountView(String userId, BigDecimal balance) {}
}