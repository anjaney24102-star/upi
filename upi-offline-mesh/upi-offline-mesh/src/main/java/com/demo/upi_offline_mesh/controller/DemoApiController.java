package com.demo.upi_offline_mesh.controller;

import com.demo.upi_offline_mesh.model.PaymentPacket;
import com.demo.upi_offline_mesh.model.PaymentReceipt;
import com.demo.upi_offline_mesh.model.PaymentTransaction;
import com.demo.upi_offline_mesh.service.MeshService;
import com.demo.upi_offline_mesh.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoApiController {

    private final PaymentService paymentService;
    private final MeshService meshService;

    public DemoApiController(PaymentService paymentService, MeshService meshService) {
        this.paymentService = paymentService;
        this.meshService = meshService;
    }

    @GetMapping("/state")
    public DashboardState state() {
        return new DashboardState(paymentService.getAccounts(), paymentService.getTransactions(), meshService.state());
    }

    @PostMapping("/payments")
    public MeshService.MeshState createPayment(@RequestBody CreatePaymentRequest request) {
        PaymentPacket packet = paymentService.createPayment(request.sender(), request.receiver(), request.amount(), request.pin());
        return meshService.inject(packet, request.sender(), request.receiver(), request.amount());
    }

    @PostMapping("/gossip")
    public MeshService.MeshState gossipRound() {
        return meshService.gossipRound();
    }

    @PostMapping("/upload/{transactionId}")
    public PaymentReceipt upload(@PathVariable String transactionId) {
        return meshService.upload(transactionId, paymentService);
    }

    @PostMapping("/reset")
    public MeshService.MeshState reset() {
        return meshService.reset();
    }

    public record CreatePaymentRequest(String sender, String receiver, BigDecimal amount, String pin) {}

    public record DashboardState(List<PaymentService.AccountView> accounts,
                                 List<PaymentTransaction> transactions,
                                 MeshService.MeshState mesh) {}
}