# AI Prompt & Use Record

In compliance with assessment guidelines, AI assistance was integrated into the engineering lifecycle. Below is the unedited audit trail of prompts, outputs, critical reviews, modifications, and verification evidence.

---

### Activity 1: Unit & Concurrency Test Case Generation
* **Target:** Designing edge-case test matrix and multi-threaded race condition tests.
* **Prompt Used:**
  > "I am designing a course waitlist service in Java with ReentrantLock. Provide a JUnit 5 test class covering: capacity saturation, duplicate enrollment rejection, FIFO promotion upon drop, prerequisite rejection, and a high-concurrency race condition test using CountDownLatch and ExecutorService with 20 concurrent threads trying to take 3 seats."
* **AI Output Summary:**
  AI generated a standard JUnit test using `ExecutorService` and `Assertions`. However, the AI solution initially used `ConcurrentLinkedQueue` in the production recommendation without proper check-then-act synchronization, causing race conditions in duplicate checks.
* **Accepted / Edited / Rejected Decision:**
  * **Accepted:** Concurrency test skeleton utilizing `CountDownLatch` for simultaneous thread release.
  * **Edited/Rejected:** Rejected the AI suggestion to use lock-free data structures for `enrolledLearners`. Instead, implemented a fair `ReentrantLock` enclosing `LinkedHashMap` to ensure deterministic check-and-act atomicity and strict FIFO ordering.
* **Verification Evidence:**
  Ran `mvn test`. The concurrency test passed consistently across 50 iterations with zero capacity breaches (`enrolled == 3`, `waitlisted == 17`).

---

### Activity 2: Waitlist Ineligible Learner Policy Strategy
* **Target:** Determining waitlist queue behavior when an ineligible learner occupies queue positions.
* **Prompt Used:**
  > "What are the common engineering trade-offs between 'Skip & Retain' vs 'Skip & Evict' when an ineligible learner reaches the head of a waitlist queue during automated seat promotion?"
* **AI Output Summary:**
  AI outlined two strategies:
  1. *Skip & Retain:* Keeps ineligible learner at head until resolved, risking blocking subsequent candidates.
  2. *Skip & Evict:* Removes the ineligible learner immediately, promoting the next valid candidate, logging the eviction.
* **Accepted / Edited / Rejected Decision:**
  * **Accepted:** Chose the *Skip & Evict* strategy to satisfy Acceptance Criterion 3 & 4 (ensuring seats open upon drop are fulfilled without queue stagnation).
* **Verification Evidence:**
  Added unit test `testFifoWaitlistPromotionAndSkippingIneligible` confirming that ineligible learners are purged and the next valid learner is promoted instantly.
