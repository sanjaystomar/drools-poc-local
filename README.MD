# Spring Boot + Drools POC — Rules from Database

Rules are stored in a `drools_rules` database table (seeded via Flyway).  
The `KieContainer` is built at startup by reading DRL content from the DB,  
and can be **hot-reloaded at runtime** without a restart.

---

## Project Structure

```
drools-poc/
├── pom.xml
└── src/main/
    ├── java/com/poc/drools/
    │   ├── DroolsPocApplication.java
    │   ├── admin/RuleAdminController.java   ← CRUD + /reload endpoint
    │   ├── config/DroolsConfig.java         ← Reads DRL from DB, builds KieContainer
    │   ├── controller/DroolsController.java ← Loan + Order endpoints
    │   ├── entity/DroolsRule.java           ← JPA entity (drools_rules table)
    │   ├── model/{LoanApplication, Order}   ← Fact POJOs
    │   ├── repository/DroolsRuleRepository  ← Spring Data JPA
    │   └── service/
    │       ├── DroolsService.java           ← Fires rules
    │       └── RuleReloadService.java       ← Hot-reload manager
    └── resources/
        ├── application.properties
        └── db/migration/
            └── V1__create_drools_rules_table.sql  ← Flyway: table + seed data
```

---

## Run

```bash
mvn spring-boot:run
```

- App: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`  
  JDBC URL: `jdbc:h2:mem:droolsdb`

---

## How Rules Are Loaded

1. **Flyway** runs `V1__create_drools_rules_table.sql` on startup → creates `drools_rules` table and seeds two rule sets
2. **`DroolsConfig.buildKieContainer()`** queries `findAllByActiveTrue()`, writes each row's `drl_content` to a virtual `KieFileSystem` path, compiles, and produces a `KieContainer`
3. **`RuleReloadService`** holds an `AtomicReference<KieContainer>` — all sessions are opened from this reference
4. **`DroolsService`** opens a fresh `KieSession` per request from the current container

---

## API Reference

### Business Endpoints

#### Evaluate a Loan
```
POST /api/loan/evaluate
```
```json
{
  "applicantName": "Jane Smith",
  "age": 32,
  "annualIncome": 90000,
  "creditScore": 760,
  "requestedAmount": 250000
}
```

#### Apply Order Discount
```
POST /api/order/discount
```
```json
{
  "customerId": "CUST001",
  "customerType": "VIP",
  "orderAmount": 1500,
  "itemCount": 12
}
```

---

### Admin Rule Management

| Method | Path                        | Description                          |
|--------|-----------------------------|--------------------------------------|
| GET    | `/api/admin/rules`          | List all rules                       |
| GET    | `/api/admin/rules/{id}`     | Get rule by ID                       |
| POST   | `/api/admin/rules`          | Create a new rule                    |
| PUT    | `/api/admin/rules/{id}`     | Update rule content / active flag    |
| DELETE | `/api/admin/rules/{id}`     | Deactivate rule (soft delete)        |
| POST   | `/api/admin/rules/reload`   | **Hot-reload** KieContainer from DB  |

#### Create a New Rule
```
POST /api/admin/rules
Content-Type: application/json

{
  "ruleName": "fraud-detection",
  "description": "Flags high-risk transactions",
  "drlContent": "package com.poc.drools.rules;\nimport com.poc.drools.model.Order;\nrule \"Flag Large Order\" ...",
  "active": true
}
```

#### Hot-Reload Rules (no restart needed)
```
POST /api/admin/rules/reload
```
```json
{
  "success": true,
  "message": "Rules reloaded successfully",
  "reloadedAt": "2024-11-15T10:30:00"
}
```

**Typical workflow to update a rule:**
1. `PUT /api/admin/rules/{id}` — update `drl_content`
2. `POST /api/admin/rules/reload` — apply changes to running engine
3. Verify with `POST /api/loan/evaluate` or `POST /api/order/discount`

---

## Switching to PostgreSQL / MySQL

In `application.properties`, replace the H2 config with:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/droolsdb
spring.datasource.username=postgres
spring.datasource.password=secret
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

And update `V1` migration: replace `AUTO_INCREMENT` with `SERIAL` (Postgres)  
or `AUTO_INCREMENT` with `INT AUTO_INCREMENT` (MySQL — no change needed).



### New Changes
## How it all fits together
```
HTTP POST /api/loan/evaluate
        │
        ▼
DroolsController.evaluateLoan()
        │
        ▼
DroolsService.evaluateLoan()
        │  opens kieContainer.newKieSession("loanSession")
        │  ← only loanBase rules are loaded in this session
        │  ← discount/fraud rules are completely isolated
        ▼
KieSession fires rules
        │
        ▼
RuleAuditListener logs each rule fired
        │
        ▼
session.dispose()
        │
        ▼
Response returned

### NOTE
The package line inside each DRL must match the addPackage("rules.<category>") you registered:


// loan-approval.drl  — stored in DB with category = "loan"
package rules.loan;                        // ← must be "rules.loan"

import com.poc.drools.model.LoanApplication;

rule "Reject Low Credit Score"
when
  $loan: LoanApplication(creditScore < 600)
then
  $loan.setStatus("REJECTED");
  update($loan);
end

// discount.drl  — stored in DB with category = "discount"
package rules.discount;                    // ← must be "rules.discount"

import com.poc.drools.model.Order;

rule "VIP Customer Discount"
when
  $order: Order(customerType == "VIP")
then
  $order.setDiscountPercentage(20.0);
  update($order);
end




## sample payload

```

{ 
 "customerId": "CUST001",
 "customerType": "VIP",
 "orderAmount": 1500,
 "itemCount": 12,
 "discountPercentage": 0,
 "finalAmount": 0,
 "discountReason": ""
 }
```

```
{
 "applicantName": "John Doe",
 "age": 30,
 "annualIncome": 80000,
 "creditScore": 720,
 "requestedAmount": 4000000,
 "approved": false,
 "status": "",
 "reason": "",
 "approvedAmount":0
}
```

curl --location 'http://localhost:8080/api/loan/evaluate' --header 'Content-Type: application/json' --data '{"applicantName": "Jane Doe","age": 20,"annualIncome": 50000,"creditScore": 800,"requestedAmount": 200000,"approved": false,"status": "","reason": "","approvedAmount":0}'
