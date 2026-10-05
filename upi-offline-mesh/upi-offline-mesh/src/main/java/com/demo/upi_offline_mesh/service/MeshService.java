package com.demo.upi_offline_mesh.service;

import com.demo.upi_offline_mesh.model.PaymentPacket;
import com.demo.upi_offline_mesh.model.PaymentReceipt;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeshService {

    private static final List<String> PHONE_ORDER = List.of("A", "B", "C", "BRIDGE");
    private static final Map<String, List<String>> NEIGHBORS = Map.of(
            "A", List.of("B"),
            "B", List.of("A", "C"),
            "C", List.of("B", "BRIDGE"),
            "BRIDGE", List.of("C"));
    private static final Map<String, String> LABELS = Map.of(
            "A", "Phone A", "B", "Phone B", "C", "Phone C", "BRIDGE", "Bridge phone");

    private final Map<String, Map<String, PaymentPacket>> inboxes = new LinkedHashMap<>();
    private final Map<String, TrackedPacket> packets = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private int gossipRounds;

    public MeshService() {
        PHONE_ORDER.forEach(phone -> inboxes.put(phone, new LinkedHashMap<>()));
    }

    public synchronized MeshState inject(PaymentPacket packet, String sender, String receiver, BigDecimal amount) {
        if (!inboxes.containsKey(sender) || !inboxes.containsKey(receiver) || "BRIDGE".equals(sender)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose sender and receiver phones A, B, or C");
        }
        if (packets.containsKey(packet.transactionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is already in this mesh");
        }
        inboxes.get(sender).put(packet.transactionId(), packet);
        packets.put(packet.transactionId(), new TrackedPacket(packet, sender, receiver, amount));
        addEvent(sender + " injected " + packet.transactionId() + " for " + receiver + " into the mesh");
        return state();
    }

    public synchronized MeshState gossipRound() {
        List<Transfer> transfers = new ArrayList<>();
        for (Map.Entry<String, Map<String, PaymentPacket>> phone : inboxes.entrySet()) {
            for (String neighbor : NEIGHBORS.get(phone.getKey())) {
                phone.getValue().forEach((transactionId, packet) -> {
                    if (!inboxes.get(neighbor).containsKey(transactionId)) {
                        transfers.add(new Transfer(phone.getKey(), neighbor, transactionId, packet));
                    }
                });
            }
        }

        gossipRounds++;
        for (Transfer transfer : transfers) {
            inboxes.get(transfer.receiver()).put(transfer.transactionId(), transfer.packet());
            addEvent("Round " + gossipRounds + ": " + transfer.sender() + " shared "
                    + transfer.transactionId() + " with " + transfer.receiver());
        }
        if (transfers.isEmpty()) {
            addEvent("Round " + gossipRounds + ": no new packet copies");
        }
        return state();
    }

    public synchronized PaymentReceipt upload(String transactionId, PaymentService paymentService) {
        if (!inboxes.get("BRIDGE").containsKey(transactionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Packet has not reached the bridge phone yet");
        }
        TrackedPacket tracked = packets.get(transactionId);
        if (tracked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown mesh packet");
        }
        PaymentReceipt receipt = paymentService.receivePayment(tracked.packet());
        tracked.markUploaded(receipt.status());
        addEvent("Bridge uploaded " + transactionId + "; backend status " + receipt.status());
        return receipt;
    }

    public synchronized MeshState state() {
        List<MeshNode> nodes = inboxes.entrySet().stream()
                .map(entry -> new MeshNode(entry.getKey(), LABELS.get(entry.getKey()), "BRIDGE".equals(entry.getKey()),
                        List.copyOf(entry.getValue().keySet())))
                .toList();
        List<MeshPacket> packetViews = packets.values().stream().map(TrackedPacket::view).toList();
        return new MeshState(nodes, packetViews, List.copyOf(events), gossipRounds);
    }

    public synchronized MeshState reset() {
        inboxes.values().forEach(Map::clear);
        packets.clear();
        events.clear();
        gossipRounds = 0;
        addEvent("Mesh reset; settled account balances and ledger are unchanged");
        return state();
    }

    private void addEvent(String event) {
        events.add(0, event);
        if (events.size() > 12) {
            events.remove(events.size() - 1);
        }
    }

    private record Transfer(String sender, String receiver, String transactionId, PaymentPacket packet) {}

    public record MeshState(List<MeshNode> nodes, List<MeshPacket> packets, List<String> events, int gossipRounds) {}
    public record MeshNode(String id, String label, boolean bridge, List<String> transactionIds) {}
    public record MeshPacket(String transactionId, String sender, String receiver, BigDecimal amount,
                             boolean uploaded, String status) {}

    private static final class TrackedPacket {
        private final PaymentPacket packet;
        private final String sender;
        private final String receiver;
        private final BigDecimal amount;
        private boolean uploaded;
        private String status = "IN MESH";

        private TrackedPacket(PaymentPacket packet, String sender, String receiver, BigDecimal amount) {
            this.packet = packet;
            this.sender = sender;
            this.receiver = receiver;
            this.amount = amount;
        }

        private PaymentPacket packet() {
            return packet;
        }

        private void markUploaded(String status) {
            this.uploaded = true;
            this.status = status;
        }

        private MeshPacket view() {
            return new MeshPacket(packet.transactionId(), sender, receiver, amount, uploaded, status);
        }
    }
}