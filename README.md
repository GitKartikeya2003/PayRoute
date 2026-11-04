# PayRouteBackend

**PayRouteBackend** is a fault-tolerant, event-driven payment gateway and merchant ledger backend built with **Java 21**, **Spring Boot**, **MySQL**, and **Apache Kafka**. 

It simulates end-to-end payment lifecycles—including merchant onboarding, order creation, idempotent payment processing, proportional fee refunds, balance settlements, double-entry-style ledger tracking, and reliable webhook delivery using the **Transactional Outbox Pattern**.

---

## Key Features

* **Secure Merchant Onboarding & Authentication:**
  * Generates unique API keys (`fp_test_...`) and webhook signing secrets (`whsec_...`).
  * Stores only SHA-256 hashes of API keys in the database (`apiKeyHash`) and authenticates requests via the `X-Api-Key` header.
* **Strict Idempotency & Concurrency Control:**
  * Protects critical financial operations (Payments, Refunds, Settlements) against duplicate network requests using the `Idempotency-Key` header (`8-100` alphanumeric/dash/underscore characters).
  * Uses **Pessimistic Write Locks** (`LockModeType.PESSIMISTIC_WRITE`) on merchant and entity records to prevent race conditions and double-spending during concurrent transactions.
* **Mock Bank Scenarios & Reconciliation:**
  * Supports multiple payment methods (`UPI`, `CARD`, `WALLET`, `NET_BANKING`) and bank simulation scenarios (`SUCCESS`, `FAILURE`, `DELAYED_SUCCESS`, `TIMEOUT`).
  * Includes a reconciliation endpoint (`/api/payments/{id}/check`) to resolve pending payments once the bank resolution window passes.
* **Automated Fee Calculation & Ledger Auditing:**
  * Automatically deducts a $2\%$ platform processing fee on successful payments:
    $$\text{fee} = \left\lfloor \frac{\text{amount} \times 2 + 99}{100} \right\rfloor$$
  * Proportionally returns processing fees on partial or full refunds.
  * Records every balance modification (`PAYMENT`, `REFUND`, `SETTLEMENT`) in an immutable `transactions` ledger table with running balances (`balanceAfter`).
* **Transactional Outbox & Kafka Event Pipeline:**
  * Guarantees zero lost notifications by persisting domain events (`payment.succeeded`, `payment.failed`, `refund.succeeded`, `settlement.created`) in the MySQL `events` table within the same database transaction before publishing to the `payroute.events` Kafka topic.
* **Signed & Retryable Webhooks:**
  * Signs outbound JSON payloads using `HmacSHA256` and the merchant's `webhookSecret` (`X-PayRoute-Signature` header).
  * Automatically retries failed webhook deliveries up to 5 times using exponential backoff ($2^{\text{attempts}}$ seconds) and validates target hosts against an allowed list to prevent SSRF attacks.

---

## System Architecture & Event Flow

```text
+--------------------+      1. Save State & Event      +--------------------+
|  Payment / Refund  | ------------------------------> |   MySQL Database   |
|  Settlement APIs   |      (Single Transaction)       |  (events & tables) |
+--------------------+                                 +--------------------+
                                                                 |
                                                                 | 2. Poll Unpublished (Every 3s)
                                                                 v
+--------------------+      3. Publish Event ID        +--------------------+
|   Kafka Broker     | <------------------------------ |     OutBoxJobs &   |
| (payroute.events)  |                                 |   OutBoxPublisher  |
+--------------------+                                 +--------------------+
          |
          | 4. Consume Event ID
          v
+--------------------+      5. Create Webhook Task     +--------------------+
|   EventListener &  | ------------------------------> |   WebhookService   |
| EventConsumerSvc   |                                 |   & WebhookJobs    |
+--------------------+                                 +--------------------+
                                                                 |
                                                                 | 6. POST Signed Payload
                                                                 |    (HmacSHA256 + Backoff Retries)
                                                                 v
                                                       +--------------------+
                                                       |  Merchant Server   |
                                                       |   (webhookUrl)     |
                                                       +--------------------+
```

---

## Tech Stack

* **Language:** Java 21
* **Framework:** Spring Boot 4.1.1 (Spring Web MVC, Spring Data JPA, Validation, Actuator, Scheduling)
* **Messaging:** Apache Kafka (`spring-boot-starter-kafka`)
* **Database:** MySQL (`mysql-connector-j`)
* **Build Tool:** Apache Maven (with Maven Wrapper `mvnw`)
* **Utilities:** Lombok, Jackson

---

## Prerequisites & Configuration

Before running the application, ensure you have the following installed and running:
1. **JDK 21**
2. **MySQL Server** (default port `3306`, database `payroutedb`)
3. **Apache Kafka** (default port `9092`)

### Environment Variables

You can configure the application using environment variables or edit `src/main/resources/application.properties` directly:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `DB_HOST` | `localhost` | MySQL database host |
| `DB_PORT` | `3306` | MySQL database port |
| `KAFKA_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `WEBHOOK_HOSTS` | `localhost,127.0.0.1,backend` | Comma-separated list of allowed hosts for merchant webhook URLs |
| `DEMO_ENABLED` | `true` | Enables demo configuration mode |

*(Note: Default MySQL credentials in `application.properties` are `root` / `root`. Create the database `payroutedb` before starting the app; Hibernate `ddl-auto=update` will automatically create all tables).*

---

## Getting Started

1. **Create the MySQL Database:**
   ```sql
   CREATE DATABASE IF NOT EXISTS payroutedb;
   ```

2. **Build and Run the Application:**
   * **Linux / macOS:**
     ```bash
     ./mvnw clean spring-boot:run
     ```
   * **Windows:**
     ```cmd
     mvnw.cmd clean spring-boot:run
     ```

3. **Health Check:**
   Once started, verify the service is up at:
   ```text
   GET http://localhost:8080/actuator/health
   ```

---

## API Reference

All protected endpoints require the `X-Api-Key` header returned during merchant registration. Mutating financial endpoints also require an `Idempotency-Key` header (8–100 characters).

### 1. Merchants (`/api/merchants`)

| Method | Endpoint | Headers | Request Body | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/merchants` | — | `{"name": "Acme Corp", "email": "pay@acme.com"}` | Registers a new merchant and returns the raw `apiKey` and `webhookSecret`. |
| `GET` | `/api/merchants/me` | `X-Api-Key` | — | Retrieves current merchant profile and balance. |
| `PUT` | `/api/merchants/me` | `X-Api-Key` | `{"name": "Acme", "email": "pay@acme.com", "webhookUrl": "http://localhost:8080/api/webhooks/test-receiver"}` | Updates merchant details and webhook destination URL. |

### 2. Orders (`/api/orders`)

| Method | Endpoint | Headers | Request Body | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/orders` | `X-Api-Key` | `{"amount": 5000, "receipt": "rcpt_101"}` | Creates a payment order (`amount` between `100` and `100000` in smallest currency unit, e.g., paise). |
| `GET` | `/api/orders` | `X-Api-Key` | — | Lists all orders for the authenticated merchant. |
| `GET` | `/api/orders/{id}` | `X-Api-Key` | — | Retrieves a specific order by ID. |

### 3. Payments (`/api/payments`)

| Method | Endpoint | Headers | Request Body | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/payments` | `X-Api-Key`, `Idempotency-Key` | `{"orderId": "order_...", "method": "UPI", "scenario": "SUCCESS"}` | Initiates a payment record for an order. Methods: `UPI`, `CARD`, `WALLET`, `NET_BANKING`. Scenarios: `SUCCESS`, `FAILURE`, `TIMEOUT`, `DELAYED_SUCCESS`. |
| `POST` | `/api/payments/{id}/process` | `X-Api-Key` | — | Processes a `CREATED` payment through the mock bank, updates ledger balance, and queues an event. |
| `POST` | `/api/payments/{id}/check` | `X-Api-Key` | — | Reconciles a `PENDING` payment (`DELAYED_SUCCESS` resolves after 8s; `TIMEOUT` resolves after 20s). |
| `GET` | `/api/payments` | `X-Api-Key`, `Idempotency-Key` | — | Retrieves a payment by its ID (passed via the `Idempotency-Key` header). |

### 4. Refunds (`/api/refunds`)

| Method | Endpoint | Headers | Request Body | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/refunds` | `X-Api-Key`, `Idempotency-Key` | `{"paymentId": "pay_...", "amount": 2500}` | Issues a full or partial refund on a `SUCCESS` payment, returning proportional fees and debiting merchant balance. |
| `GET` | `/api/refunds` | `X-Api-Key` | — | Lists all refunds for the merchant. |

### 5. Settlements (`/api/settlements`)

| Method | Endpoint | Headers | Request Body | Description |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/settlements` | `X-Api-Key`, `Idempotency-Key` | — | Settles the merchant's entire available balance, recording a `SETTLEMENT` ledger entry and emitting `settlement.created`. |
| `GET` | `/api/settlements` | `X-Api-Key` | — | Lists past settlements for the merchant. |

### 6. Transactions & Ledger (`/api/transactions`)

| Method | Endpoint | Headers | Query Params | Description |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/transactions` | `X-Api-Key` | `status`, `from` (`YYYY-MM-DD`), `to` (`YYYY-MM-DD`) | Filters merchant payments by status and UTC date range. |
| `GET` | `/api/transactions/ledger` | `X-Api-Key` | — | Returns the chronological audit trail of all credits and debits (`PAYMENT`, `REFUND`, `SETTLEMENT`). |
| `GET` | `/api/transactions/{id}` | `X-Api-Key` | — | Retrieves details for a specific payment ID. |

### 7. Webhooks (`/api/webhooks`)

| Method | Endpoint | Headers | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/webhooks` | `X-Api-Key` | Lists all webhook delivery records (`PENDING`, `DELIVERED`, `FAILED`, `SKIPPED`) for the merchant. |
| `POST` | `/api/webhooks/{id}/retry` | `X-Api-Key` | Resets attempt count and immediately retries an undelivered webhook. |
| `POST` | `/api/webhooks/test-receiver` | `X-PayRoute-Event`, `X-PayRoute-Signature` | Built-in test receiver endpoint that logs incoming webhook headers and JSON payloads to the console. |

---

## Testing Webhooks Locally (Quick Walkthrough)

1. **Register a Merchant** via `POST /api/merchants` and copy the `apiKey` and `webhookSecret`.
2. **Set the Webhook URL** via `PUT /api/merchants/me` with:
   ```json
   {
     "name": "Demo Store",
     "email": "demo@store.com",
     "webhookUrl": "http://localhost:8080/api/webhooks/test-receiver"
   }
   ```
3. **Create an Order** (`POST /api/orders`), **Create a Payment** (`POST /api/payments`), and **Process the Payment** (`POST /api/payments/{id}/process`).
4. Watch your application logs: within 3 seconds, `OutBoxJobs` will publish the `payment.succeeded` event to Kafka, `EventListener` will consume it, and `WebhookController.receiveTestWebhook()` will log the event ID, `HmacSHA256` signature, and JSON payload.