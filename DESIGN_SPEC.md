# Design Specification: Course Waitlist & Registration Service

## 1. Service Boundary & Scope
This service models a bounded domain slice handling enrollment, cancellation (drop), and FIFO-ordered waitlist promotion for an individual course offering. In alignment with requirements, unnecessary architectural overhead (external microservices, relational databases, web UIs, payment gateways) is omitted in favor of an atomic, thread-safe, in-memory domain model.

## 2. Data & API Model
* **Learner:** Immutable domain entity containing `id`, `name`, and prerequisite satisfaction boolean flag `meetsPrerequisite`.
* **EnrollmentStatus:** Explicit outcome enumeration (`ENROLLED`, `WAITLISTED`, `DROPPED`, `ALREADY_ENROLLED`, `ALREADY_WAITLISTED`, `PREREQUISITE_NOT_MET`, `NOT_FOUND`, `INVALID_REQUEST`).
* **EnrollmentResult:** Immutable value object returning success flags, status, descriptive audit messages, and any candidate learner promoted during state transitions.
* **CourseOfferingService:**
  * `enroll(Learner learner): EnrollmentResult`
  * `drop(String learnerId): EnrollmentResult`
  * State queries: `getEnrolledLearners()`, `getWaitlistLearners()`, `getCapacity()`.

## 3. Capacity and Queue Invariants
1. **Capacity Limit:** `enrolledLearners.size() <= capacity` strictly holds at all times.
2. **Uniqueness:** A learner ID may exist in `enrolledLearners` OR `waitlistLearners`, never both, and cannot be duplicated in either.
3. **FIFO Order:** Waitlist promotion maintains exact FIFO ordering using an insertion-ordered queue structure.

## 4. Prerequisite & Waitlist Ineligibility Policy
* **Prerequisite Rule:** A learner must satisfy prerequisites before gaining admission or entering the waitlist.
* **Waitlist Head Ineligibility Policy (Skip & Evict):**
  If an ineligible learner reaches the head of the waitlist during seat vacancy promotion, the service **skips and purges** the invalid record from the queue, immediately evaluating the subsequent candidate. This prevents deadlock ("queue head blockage") where valid learners are trapped behind an unfulfillable record.

## 5. Concurrency & Synchronization
* **Mechanism:** ReentrantLock (fairness mode enabled) is used across mutations (`enroll`, `drop`).
* **Rationale:** Eliminates race conditions between read-check and write sequences (e.g., two concurrent threads reading `size < capacity` at the same instant). ReentrantLock provides guaranteed atomic execution without thread starvation.

## 6. Trade-offs & Limitations
* **In-Memory Volatility:** State is stored in JVM heap memory. A service restart flushes state. For enterprise distributed scaling, this service slice would back onto a distributed cache (Redis Sorted Sets) or a relational DB with pessimistic row-level locking (`SELECT FOR UPDATE`).
