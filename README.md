# Course Waitlist & Registration Service

A thread-safe, bounded course offering slice providing enrollment, dropping, and FIFO waitlist promotions with automated verification.

## Prerequisites
* Java JDK 17 or higher
* Apache Maven 3.8+
* Git

## Project Structure
```text
.
├── pom.xml
├── README.md
├── DESIGN_SPEC.md
├── AI_PROMPT_LOG.md
└── src
    ├── main/java/com/schwab/assessment
    │   ├── model
    │   │   ├── EnrollmentResult.java
    │   │   ├── EnrollmentStatus.java
    │   │   └── Learner.java
    │   └── service
    │       └── CourseOfferingService.java
    └── test/java/com/schwab/assessment/service
        └── CourseOfferingServiceTest.java
```

## Setup & Execution Commands

### 1. Build and Run Automated Tests
Execute the comprehensive test suite (including boundary, capacity, duplicate, and concurrency tests):
```bash
mvn clean test
```

### 2. Package JAR
```bash
mvn clean package
```

## Key Invariants & Guarantees
1. **Thread-Safe Capacity Enforcement:** No over-subscription under concurrent access.
2. **Duplicate Protection:** Rejects duplicate enrollments and waitlist requests idempotently.
3. **Deterministic FIFO Queue:** Preserves exact arrival ordering for promotion.
4. **Self-Healing Waitlist:** Ineligible candidates are safely skipped and evicted during promotion without blocking seats.
