# Spring Boot + Drools POC — Rules from Database

A proof-of-concept demonstrating Drools rule engine integration with Spring Boot.  
Rules are stored in a `drools_rules` database table, compiled into isolated `KieBase`s  
per category, and can be **hot-reloaded at runtime** without restarting the application.

---

## Table of Contents

1. [Project Structure](#project-structure)
2. [How It Works](#how-it-works)
3. [Key Design Decisions](#key-design-decisions)
4. [Database Schema](#database-schema)
5. [DRL Authoring Guidelines](#drl-authoring-guidelines)
6. [Named Sessions — No kmodule.xml](#named-sessions--no-kmodulexml)
7. [Stateful vs Stateless Sessions](#stateful-vs-stateless-sessions)
8. [Logging in Rules](#logging-in-rules)
9. [Running the Application](#running-the-application)
10. [API Reference](#api-reference)
11. [Switching to PostgreSQL / MySQL](#switching-to-postgresql--mysql)

---

## Project Structure

```
drools-poc/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/poc/drools/
    │   │   ├── DroolsPocApplication.java
    │   │   ├── admin/
    │   │   │   └── RuleAdminController.java      ← CRUD + /reload endpoint
    │   │   ├── config/
    │   │   │   └── DroolsConfig.java             ← Builds KieContainer from DB using KieModuleModel
    │   │   ├── controller/
    │   │   │   └── DroolsController.java         ← Loan + Order REST endpoints
    │   │   ├── entity/
    │   │   │   └── DroolsRule.java               ← JPA entity (drools_rules table)
    │   │   ├── model/
    │   │   │   ├── LoanApplication.java          ← Fact POJO with isStatusUnset() helper
    │   │   │   └── Order.java                    ← Fact POJO
    │   │   ├── repository/
    │   │   │   └── DroolsRuleRepository.java     ← Spring Data JPA
    │   │   └── service/
    │   │       ├── DroolsService.java            ← Stateful + stateless execution
    │   │       ├── RuleAuditListener.java        ← Logs rule firing via AgendaEventListener
    │   │       └── RuleReloadService.java        ← Hot-reload manager (AtomicReference)
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           ├── V1__create_drools_rules_table.sql   ← Table + seeded loan & discount rules
    │           └── V2__add_category_to_rules.sql       ← Adds category column for KieBase isolation
    └── test/
        └── java/com/poc/drools/service/
            └── DroolsServiceTest.java
```

---

## How It Works

```
Application Startup
       │
       ▼
Flyway runs migrations
  V1 → creates drools_rules table, seeds loan-approval + discount rules
  V2 → adds category column (loan / discount)
       │
       ▼
DroolsConfig.buildKieContainer()
  1. Queries findAllByActiveTrue()
  2. Groups rules by category
  3. Builds KieModuleModel programmatically (no kmodule.xml file)
     └─ creates one KieBase + stateful/stateless KieSession per category
  4. Writes KModuleXML + DRL content into KieFileSystem (virtual, in-memory)
  5. Compiles and returns KieContainer
       │
       ▼
RuleReloadService holds AtomicReference<KieContainer>
       │
       ▼
HTTP Request arrives
       │
       ▼
DroolsService opens named KieSession from current KieContainer
  └─ stateful  → session.insert() → fireAllRules() → dispose()
  └─ stateless → session.execute(fact)  [no dispose needed]
       │
       ▼
RuleAuditListener logs each rule fired (via AgendaEventListener)
       │
       ▼
Response returned
```

---

## Key Design Decisions

### Rules stored in database
DRL content lives in the `drools_rules` table. This means rules can be updated, activated,
or deactivated without redeployment. After updating a row, call `POST /api/admin/rules/reload`
to apply changes to the running engine.

### No kmodule.xml file
`kmodule.xml` is generated **in memory** using `KieModuleModel` and fed to `KieFileSystem`
via `kfs.writeKModuleXML(moduleModel.toXML())`. This gives full control over KieBase and
KieSession configuration from Java code, with no XML file on disk.

### One KieBase per category
Each rule category (`loan`, `discount`, etc.) gets its own isolated `KieBase`. This means:
- Loan rules never fire in a discount session and vice versa
- Adding a new category is automatic — just insert a row with a new `category` value and reload

### Stateful and stateless sessions per category
Each category registers both a stateful session (`loanSession`) and a stateless session
(`loanStatelessSession`). The service chooses the appropriate one based on whether the
rules use `update()` / `retract()`.

---

## Database Schema

### `drools_rules` table

| Column       | Type         | Description                                              |
|--------------|--------------|----------------------------------------------------------|
| `id`         | BIGINT (PK)  | Auto-generated primary key                               |
| `rule_name`  | VARCHAR(100) | Unique logical name (e.g. `loan-approval`)               |
| `category`   | VARCHAR(50)  | Maps to KieBase package — e.g. `loan`, `discount`        |
| `description`| VARCHAR(500) | Human-readable description                               |
| `drl_content`| TEXT         | Full DRL source loaded into the rule engine              |
| `active`     | BOOLEAN      | Only active rules are compiled into the KieContainer     |
| `created_at` | TIMESTAMP    | Set on insert                                            |
| `updated_at` | TIMESTAMP    | Updated on every save                                    |

### Adding a new rule domain

1. Insert a row into `drools_rules` with a new `category` (e.g. `fraud`)
2. Ensure the `package` declaration inside the DRL matches `rules.<category>`:
   ```drl
   package rules.fraud;
   ```
3. Call `POST /api/admin/rules/reload` — a new `fraudBase` + `fraudSession` is created automatically

---

## DRL Authoring Guidelines

### Package declaration must match category

The `package` line inside every DRL file must match `rules.<category>` exactly:

```drl
-- category = "loan" in DB
package rules.loan;

import com.poc.drools.model.LoanApplication;
```

### Always guard rules to prevent infinite re-firing

When a rule calls `update()`, Drools re-evaluates all rules against the updated fact.
Without a guard condition, the same rule can match and fire repeatedly.

**Wrong — will loop:**
```drl
rule "Reject Low Credit Score"
    when
        $loan: LoanApplication(creditScore < 600)
    then
        $loan.setStatus("REJECTED");
        update($loan);           // re-evaluation triggers this rule again → infinite loop
end
```

**Correct — guarded with status check:**
```drl
rule "Reject Low Credit Score"
    no-loop true
    when
        $loan: LoanApplication(
            creditScore < 600,
            status == null || status == ""    // only fires if status not yet set
        )
    then
        modify($loan) {
            setApproved(false),
            setStatus("REJECTED"),
            setReason("Credit score below minimum threshold")
        }
end
```

### Preferred ways to check for unset status in `when`

```drl
-- 1. Null check only
$loan: LoanApplication(status == null)

-- 2. Null or empty string
$loan: LoanApplication(status == null || status == "")

-- 3. Using model helper method (recommended — centralises the logic)
$loan: LoanApplication(isStatusUnset() == true)
```

Add `isStatusUnset()` to `LoanApplication.java`:
```java
public boolean isStatusUnset() {
    return status == null || status.isEmpty();
}
```

### Use `modify` instead of multiple `update` calls

`modify` batches all field changes into a single re-evaluation notification,
reducing unnecessary rule re-triggering:

```drl
-- Preferred
modify($loan) {
    setApproved(false),
    setStatus("REJECTED"),
    setReason("Does not meet criteria")
}

-- Avoid — each setter may trigger re-evaluation separately
$loan.setApproved(false);
$loan.setStatus("REJECTED");
update($loan);
```

### Rule attributes reference

| Attribute        | Effect                                                              |
|------------------|---------------------------------------------------------------------|
| `salience N`     | Higher number fires first (default 0)                              |
| `no-loop true`   | Rule won't re-fire due to its own `update()`                       |
| `lock-on-active` | Rule won't re-fire due to any update while on the agenda           |

---

## Named Sessions — No kmodule.xml

Sessions are declared programmatically in `DroolsConfig` using `KieModuleModel`:

```java
KieBaseModel kbaseModel = moduleModel
        .newKieBaseModel("loanBase")
        .addPackage("rules.loan")
        .setDefault(false);

// Stateful session
kbaseModel.newKieSessionModel("loanSession")
        .setType(KieSessionModel.KieSessionType.STATEFUL);

// Stateless session
kbaseModel.newKieSessionModel("loanStatelessSession")
        .setType(KieSessionModel.KieSessionType.STATELESS);
```

Sessions are then looked up by name in `DroolsService`:

```java
// Stateful
KieSession session = kieContainer.newKieSession("loanSession");

// Stateless
StatelessKieSession session = kieContainer.newStatelessKieSession("discountStatelessSession");
```

No `kmodule.xml` file is needed — the equivalent XML is generated in memory via
`kfs.writeKModuleXML(moduleModel.toXML())`.

---

## Stateful vs Stateless Sessions

| Feature                    | Stateful                        | Stateless                          |
|----------------------------|---------------------------------|------------------------------------|
| `update()` / `retract()`   | ✅ Supported                    | ❌ Not supported                   |
| Multi-pass rule chaining   | ✅ Rules can trigger each other | ❌ Single pass only                |
| `dispose()` required       | ✅ Always call after use        | ❌ Not needed                      |
| Working memory queries     | ✅ Supported                    | ❌ Not supported                   |
| Performance                | Slightly heavier                | Faster, lower memory               |
| Best for                   | Loan approval, complex workflows| Validation, discounts, simple eval |

### Stateless execution variants in `DroolsService`

```java
// Single fact
session.execute(fact);

// Multiple facts
session.execute(CommandFactory.newInsertElements(facts));

// With globals (e.g. pass a Spring service into a rule)
List<Command<?>> commands = new ArrayList<>();
commands.add(CommandFactory.newSetGlobal("notificationService", notificationService));
commands.add(CommandFactory.newInsert(fact));
commands.add(CommandFactory.newFireAllRules());
session.execute(CommandFactory.newBatchExecution(commands));
```

Using a global in DRL:
```drl
global com.poc.drools.service.NotificationService notificationService;

rule "Notify On Rejection"
    when
        $loan: LoanApplication(approved == false, isStatusUnset() == false)
    then
        notificationService.notify("Loan rejected: " + $loan.getApplicantName());
end
```

---

## Logging in Rules

### Option 1 — Static logger helper (for `then` block)

Add a utility class:
```java
public class DroolsLogger {
    public static void info(String msg) {
        LoggerFactory.getLogger("DroolsRules").info(msg);
    }
    public static boolean debugAndReturn(String msg) {
        LoggerFactory.getLogger("DroolsRules").debug(msg);
        return true;   // for use inside eval()
    }
}
```

Use in DRL:
```drl
import com.poc.drools.util.DroolsLogger;

rule "Reject Low Credit Score"
    when
        $loan: LoanApplication(creditScore < 600, isStatusUnset() == true)
    then
        DroolsLogger.info("Rejecting: " + $loan.getApplicantName());
        modify($loan) { setStatus("REJECTED") }
end
```

### Option 2 — `AgendaEventListener` (recommended for production)

`RuleAuditListener` is registered on every session in `DroolsService` and logs all
rule activity globally without touching any DRL file:

```java
@Component
public class RuleAuditListener extends DefaultAgendaEventListener {
    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        log.debug("BEFORE | rule='{}'", event.getMatch().getRule().getName());
    }
    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        log.info("FIRED  | rule='{}'", event.getMatch().getRule().getName());
    }
}
```

### Option 3 — `eval()` for logging in the `when` clause

The `when` clause is declarative and does not support direct logging. Use `eval()` as
a workaround (debug/dev only — `eval()` disables Drools indexing and hurts performance):

```drl
rule "Debug Matching"
    when
        $loan: LoanApplication()
        eval(DroolsLogger.debugAndReturn("Evaluating: " + $loan.getApplicantName()))
    then
        // actions
end
```

---

## Running the Application

### Prerequisites

- Java 17+
- Maven 3.8+

### Start

```bash
mvn spring-boot:run
```

- App: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`
    - JDBC URL: `jdbc:h2:mem:droolsdb`
    - Username: `sa` / Password: _(blank)_

### Run Tests

```bash
mvn test
```

---

## API Reference

### Business Endpoints

#### Evaluate a Loan — Stateful
```
POST /api/loan/evaluate
Content-Type: application/json
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
**Response:**
```json
{
  "applicantName": "Jane Smith",
  "age": 32,
  "annualIncome": 90000,
  "creditScore": 760,
  "requestedAmount": 250000,
  "approved": true,
  "status": "APPROVED",
  "reason": "Excellent credit profile - full amount approved",
  "approvedAmount": 250000.0
}
```

**Loan rule outcomes:**

| Condition                               | Status                    |
|-----------------------------------------|---------------------------|
| `creditScore < 600`                     | `REJECTED`                |
| `age < 18`                              | `REJECTED`                |
| `requestedAmount > annualIncome × 5`    | `REJECTED`                |
| `creditScore >= 750`, `income >= 50000` | `APPROVED` (100%)         |
| `creditScore 650–749`, `income >= 30k`  | `CONDITIONALLY_APPROVED` (75%) |
| Anything else                           | `REJECTED`                |

---

#### Apply Order Discount — Stateless
```
POST /api/order/discount
Content-Type: application/json
```
```json
{
  "customerId": "CUST001",
  "customerType": "VIP",
  "orderAmount": 1500,
  "itemCount": 12
}
```
**Response:**
```json
{
  "customerId": "CUST001",
  "customerType": "VIP",
  "orderAmount": 1500,
  "itemCount": 12,
  "discountPercentage": 25.0,
  "finalAmount": 1125.0,
  "discountReason": "VIP customer - 20% discount + Bulk order bonus 5%"
}
```

**Discount rule outcomes:**

| Condition                                  | Discount      |
|--------------------------------------------|---------------|
| `customerType = VIP`                       | 20%           |
| `customerType = PREMIUM`                   | 10%           |
| `itemCount >= 10` AND `orderAmount >= 500` | +5% (stacks)  |
| `orderAmount >= 1000` AND `REGULAR` type   | 7%            |
| None of the above                          | 0%            |

---

### Admin Rule Management

| Method | Path                      | Description                         |
|--------|---------------------------|-------------------------------------|
| GET    | `/api/admin/rules`        | List all rules (active + inactive)  |
| GET    | `/api/admin/rules/{id}`   | Get rule by ID                      |
| POST   | `/api/admin/rules`        | Create a new rule                   |
| PUT    | `/api/admin/rules/{id}`   | Update rule content / active flag   |
| DELETE | `/api/admin/rules/{id}`   | Soft-deactivate a rule              |
| POST   | `/api/admin/rules/reload` | Hot-reload KieContainer from DB     |

#### Create a New Rule
```
POST /api/admin/rules
Content-Type: application/json
```
```json
{
  "ruleName": "fraud-detection",
  "category": "fraud",
  "description": "Flags high-risk transactions",
  "drlContent": "package rules.fraud;\nimport com.poc.drools.model.Order;\nrule \"Flag Large Order\" when $o: Order(orderAmount > 10000) then ...",
  "active": true
}
```

#### Update a Rule
```
PUT /api/admin/rules/1
Content-Type: application/json
```
```json
{
  "description": "Updated loan rules",
  "drlContent": "package rules.loan; ...",
  "active": true
}
```

#### Hot-Reload (no restart required)
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

**Workflow to update a live rule:**
1. `PUT /api/admin/rules/{id}` — edit `drl_content` or toggle `active`
2. `POST /api/admin/rules/reload` — atomically rebuilds and swaps the KieContainer
3. Verify with the relevant business endpoint

---

## Switching to PostgreSQL / MySQL

Replace the H2 config in `application.properties`:

```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/droolsdb
spring.datasource.username=postgres
spring.datasource.password=secret
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/droolsdb
spring.datasource.username=root
spring.datasource.password=secret
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
```

Update the Flyway migration for PostgreSQL — replace `AUTO_INCREMENT` with `SERIAL`:
```sql
CREATE TABLE drools_rules (
    id SERIAL PRIMARY KEY,
    ...
);
```

For MySQL, `AUTO_INCREMENT` syntax is unchanged.
