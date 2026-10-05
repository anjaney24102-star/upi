package com.demo.upi_offline_mesh.service;

import com.demo.upi_offline_mesh.model.DemoAccount;
import com.demo.upi_offline_mesh.model.PaymentInstruction;
import com.demo.upi_offline_mesh.model.PaymentReceipt;
import com.demo.upi_offline_mesh.model.PaymentTransaction;
import com.demo.upi_offline_mesh.repository.DemoAccountRepository;
import com.demo.upi_offline_mesh.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentSettlementService {

    private final DemoAccountRepository accountRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PinHasher pinHasher;

    public PaymentSettlementService(
            DemoAccountRepository accountRepository,
            PaymentTransactionRepository transactionRepository,
            PinHasher pinHasher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.pinHasher = pinHasher;
    }

    @Transactional
    public PaymentReceipt settle(PaymentInstruction instruction) {
        if (instruction.senderId().equals(instruction.receiverId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender and receiver must differ");
        }

        List<String> lockOrder = List.of(instruction.senderId(), instruction.receiverId()).stream().sorted().toList();
        Map<String, DemoAccount> lockedAccounts = new HashMap<>();
        for (String userId : lockOrder) {
            DemoAccount account = accountRepository.findLockedByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown account: " + userId));
            lockedAccounts.put(userId, account);
        }

        if (transactionRepository.existsById(instruction.transactionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment has already been settled");
        }

        DemoAccount sender = lockedAccounts.get(instruction.senderId());
        DemoAccount receiver = lockedAccounts.get(instruction.receiverId());
        if (!pinHasher.matches(instruction.pin(), sender.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect sender PIN");
        }
        BigDecimal amount = instruction.amount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment amount");
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }

        sender.debit(amount);
        receiver.credit(amount);
        transactionRepository.saveAndFlush(new PaymentTransaction(instruction));
        return new PaymentReceipt(
                instruction.transactionId(), amount, instruction.senderId(), instruction.receiverId(),
                instruction.timestamp(), "SETTLED");
    }
}