## MeshPay offline UPI protocol simulator

This is a Java-only local demo of an offline payment packet moving through a phone mesh. Spring Boot serves the dashboard and APIs, and each run stores accounts and the settlement ledger in an embedded in-memory H2 database. There is no external database, Bluetooth dependency, hosted asset, or Internet connection in the payment flow.

### Run it

Use Java 25 and build once when Gradle dependencies are available:

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

Open [http://localhost:8080](http://localhost:8080). The demo seeds accounts A, B, and C with balances of ₹5,000, ₹3,000, and ₹2,000. Their demo PIN is `1234`.

### Walk through a payment

1. Select A as sender, B as receiver, enter `500.00` and PIN `1234`, then inject the payment.
2. Run gossip rounds. Each round copies opaque AES-GCM packet bytes across one neighbor link in the simulated chain `A → B → C → Bridge`.
3. When the bridge has the packet, choose **Upload to backend**.
4. The backend authenticates and decrypts the packet, checks the transaction ID and expiry, verifies the sender PIN, locks both account rows, rejects replayed transaction IDs, checks funds, moves the balance, and writes one `SETTLED` ledger row in the same database transaction.

The dashboard refreshes account balances and the ledger after settlement. **Reset mesh** clears only simulated phone caches; it deliberately keeps balances and settled transactions.

### Protocol and safety notes

The demo uses AES-256-GCM with transaction ID as authenticated additional data. The PIN is included only inside the encrypted packet and is stored at rest as a salted PBKDF2 hash. A fixed demo encryption key makes the app start without setup; set `PAYMENT_ENCRYPTION_KEY` to a Base64-encoded random 32-byte key outside the demo. This is an educational protocol simulator, not banking software: shared-key provisioning and PIN entry are simplified, and the built-in key/PINs must never be used for real funds.

Run `.\gradlew.bat test` to execute the tests, including concurrent uploads of the same packet and assertions that only one settlement debits the sender.
