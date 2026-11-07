package com.schwab.assessment.service;

import com.schwab.assessment.model.EnrollmentResult;
import com.schwab.assessment.model.EnrollmentStatus;
import com.schwab.assessment.model.Learner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CourseOfferingServiceTest {

    private CourseOfferingService service;
    private static final int TEST_CAPACITY = 3;

    @BeforeEach
    void setUp() {
        service = new CourseOfferingService("CS-601", TEST_CAPACITY);
    }

    @Test
    @DisplayName("Criterion 1: Enrollment succeeds when capacity remains and prerequisite is met")
    void testEnrollmentSuccessWithinCapacity() {
        Learner learner = new Learner("L1", "Alice", true);
        EnrollmentResult result = service.enroll(learner);

        assertTrue(result.isSuccess());
        assertEquals(EnrollmentStatus.ENROLLED, result.getStatus());
        assertEquals(1, service.getEnrolledCount());
        assertEquals(0, service.getWaitlistCount());
        assertTrue(service.getEnrolledLearners().contains(learner));
    }

    @Test
    @DisplayName("Criterion 2: Course full places eligible learner onto waitlist; duplicates rejected")
    void testEnrollmentWaitlistWhenFullAndDuplicateHandling() {
        service.enroll(new Learner("L1", "Alice", true));
        service.enroll(new Learner("L2", "Bob", true));
        service.enroll(new Learner("L3", "Charlie", true));

        assertEquals(3, service.getEnrolledCount());

        // 4th learner should be waitlisted
        Learner waitlistedLearner = new Learner("L4", "David", true);
        EnrollmentResult waitlistResult = service.enroll(waitlistedLearner);
        assertTrue(waitlistResult.isSuccess());
        assertEquals(EnrollmentStatus.WAITLISTED, waitlistResult.getStatus());
        assertEquals(1, service.getWaitlistCount());

        // Duplicate enrollment attempt for already enrolled learner
        EnrollmentResult dupEnrolled = service.enroll(new Learner("L1", "Alice Clone", true));
        assertFalse(dupEnrolled.isSuccess());
        assertEquals(EnrollmentStatus.ALREADY_ENROLLED, dupEnrolled.getStatus());

        // Duplicate enrollment attempt for already waitlisted learner
        EnrollmentResult dupWaitlisted = service.enroll(new Learner("L4", "David Clone", true));
        assertFalse(dupWaitlisted.isSuccess());
        assertEquals(EnrollmentStatus.ALREADY_WAITLISTED, dupWaitlisted.getStatus());

        // State remains uncorrupted
        assertEquals(3, service.getEnrolledCount());
        assertEquals(1, service.getWaitlistCount());
    }

    @Test
    @DisplayName("Criterion 3 & 4: FIFO waitlist promotion upon drop, skipping ineligible learners")
    void testFifoWaitlistPromotionAndSkippingIneligible() {
        service.enroll(new Learner("L1", "Alice", true));
        service.enroll(new Learner("L2", "Bob", true));
        service.enroll(new Learner("L3", "Charlie", true));

        Learner eligibleWaitlist1 = new Learner("L4", "David", true);
        service.enroll(eligibleWaitlist1);

        Learner eligibleWaitlist2 = new Learner("L5", "Eve", true);
        service.enroll(eligibleWaitlist2);

        // Drop L2 -> L4 should be promoted first (FIFO)
        EnrollmentResult dropResult = service.drop("L2");
        assertTrue(dropResult.isSuccess());
        assertEquals(EnrollmentStatus.DROPPED, dropResult.getStatus());
        assertTrue(dropResult.getPromotedLearner().isPresent());
        assertEquals("L4", dropResult.getPromotedLearner().get().getId());

        assertEquals(3, service.getEnrolledCount());
        assertEquals(1, service.getWaitlistCount());
        assertTrue(service.getEnrolledLearners().contains(eligibleWaitlist1));
        assertFalse(service.getEnrolledLearners().contains(new Learner("L2", "Bob", true)));
    }

    @Test
    @DisplayName("Criterion 4: Prerequisite failure rejects enrollment and waitlist")
    void testPrerequisiteFailureRejected() {
        Learner unqualified = new Learner("L99", "Unqualified John", false);
        EnrollmentResult result = service.enroll(unqualified);

        assertFalse(result.isSuccess());
        assertEquals(EnrollmentStatus.PREREQUISITE_NOT_MET, result.getStatus());
        assertEquals(0, service.getEnrolledCount());
        assertEquals(0, service.getWaitlistCount());
    }

    @Test
    @DisplayName("Criterion 5: Invalid requests and duplicate drops return clear results without corrupting state")
    void testInvalidRequestsAndDuplicateDrops() {
        // Drop non-existent
        EnrollmentResult notFoundDrop = service.drop("NON_EXISTENT");
        assertFalse(notFoundDrop.isSuccess());
        assertEquals(EnrollmentStatus.NOT_FOUND, notFoundDrop.getStatus());

        // Null / Blank checks
        assertFalse(service.enroll(null).isSuccess());
        assertEquals(EnrollmentStatus.INVALID_REQUEST, service.enroll(null).getStatus());
        assertFalse(service.drop("").isSuccess());
        assertEquals(EnrollmentStatus.INVALID_REQUEST, service.drop("").getStatus());

        // Normal enroll and double drop
        service.enroll(new Learner("L1", "Alice", true));
        EnrollmentResult firstDrop = service.drop("L1");
        assertTrue(firstDrop.isSuccess());

        EnrollmentResult secondDrop = service.drop("L1");
        assertFalse(secondDrop.isSuccess());
        assertEquals(EnrollmentStatus.NOT_FOUND, secondDrop.getStatus());
        assertEquals(0, service.getEnrolledCount());
    }

    @Test
    @DisplayName("Criterion 6: High concurrency test preserving invariants under race conditions")
    void testConcurrentEnrollmentAndDroppingInvariants() throws InterruptedException, ExecutionException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<EnrollmentResult>> futures = new ArrayList<>();

        for (int i = 1; i <= threadCount; i++) {
            final String id = "C_USER_" + i;
            futures.add(executor.submit(() -> {
                latch.await();
                return service.enroll(new Learner(id, "Learner " + id, true));
            }));
        }

        // Start all threads simultaneously
        latch.countDown();

        int enrolledCount = 0;
        int waitlistedCount = 0;
        for (Future<EnrollmentResult> f : futures) {
            EnrollmentResult res = f.get();
            if (res.getStatus() == EnrollmentStatus.ENROLLED) enrolledCount++;
            if (res.getStatus() == EnrollmentStatus.WAITLISTED) waitlistedCount++;
        }

        executor.shutdown();

        // Exact invariants verification
        assertEquals(TEST_CAPACITY, service.getEnrolledCount(), "Enrolled count must not exceed capacity");
        assertEquals(TEST_CAPACITY, enrolledCount, "Exactly capacity count threads should receive ENROLLED");
        assertEquals(threadCount - TEST_CAPACITY, service.getWaitlistCount());
        assertEquals(threadCount - TEST_CAPACITY, waitlistedCount);
    }
}
