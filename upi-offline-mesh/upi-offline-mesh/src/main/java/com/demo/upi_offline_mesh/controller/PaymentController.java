package com.demo.upi_offline_mesh.controller;

import com.demo.upi_offline_mesh.model.PaymentPacket;
import com.demo.upi_offline_mesh.model.PaymentReceipt;
import com.demo.upi_offline_mesh.model.PaymentTransaction;
import com.demo.upi_offline_mesh.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentPacket createPayment(@RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(request.sender(), request.receiver(), request.amount(), request.pin());
    }

    @PostMapping("/receive")
    public PaymentReceipt receivePayment(@RequestBody PaymentPacket packet) {
        return paymentService.receivePayment(packet);
    }

    @GetMapping("/transactions")
    public List<PaymentTransaction> getTransactions() {
        return paymentService.getTransactions();
    }

    public record CreatePaymentRequest(String sender, String receiver, BigDecimal amount, String pin) {}
}