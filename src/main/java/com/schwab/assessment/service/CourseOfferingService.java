package com.schwab.assessment.service;

import com.schwab.assessment.model.EnrollmentResult;
import com.schwab.assessment.model.EnrollmentStatus;
import com.schwab.assessment.model.Learner;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe implementation of course registration, drops, and FIFO waitlist promotions.
 *
 * Policy Assumptions:
 * 1. Prerequisite Rule: A learner must have meetsPrerequisite() == true to enroll or be waitlisted.
 * 2. Uniqueness: A learner can only be enrolled once. Duplicate enrollment or waitlist attempts return
 *    clear failure statuses without corrupting state.
 * 3. FIFO Waitlist Promotion: Dropping an enrolled learner opens 1 seat. The earliest eligible waitlisted
 *    learner is atomically promoted.
 * 4. Ineligible Queue Policy (Skip & Evict): If an ineligible learner somehow reaches the head of the waitlist
 *    (e.g., status revoked), they are skipped and evicted from the queue to maintain queue vitality,
 *    and the next eligible learner is evaluated.
 */
public class CourseOfferingService {
    private final String courseCode;
    private final int capacity;

    // Concurrent synchronization lock for state mutation
    private final ReentrantLock lock = new ReentrantLock(true); // fair lock

    // In-memory state tracking
    private final Map<String, Learner> enrolledLearners = new LinkedHashMap<>();
    private final Map<String, Learner> waitlistLearners = new LinkedHashMap<>(); // Maintains FIFO insertion order

    public CourseOfferingService(String courseCode, int capacity) {
        if (courseCode == null || courseCode.isBlank()) {
            throw new IllegalArgumentException("Course code cannot be null or blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero");
        }
        this.courseCode = courseCode.trim();
        this.capacity = capacity;
    }

    public EnrollmentResult enroll(Learner learner) {
        if (learner == null) {
            return EnrollmentResult.failure(EnrollmentStatus.INVALID_REQUEST, "Learner cannot be null");
        }

        lock.lock();
        try {
            String learnerId = learner.getId();

            // 1. Duplicate check in enrolled
            if (enrolledLearners.containsKey(learnerId)) {
                return EnrollmentResult.failure(
                        EnrollmentStatus.ALREADY_ENROLLED,
                        "Learner " + learnerId + " is already enrolled in " + courseCode
                );
            }

            // 2. Duplicate check in waitlist
            if (waitlistLearners.containsKey(learnerId)) {
                return EnrollmentResult.failure(
                        EnrollmentStatus.ALREADY_WAITLISTED,
                        "Learner " + learnerId + " is already on the waitlist for " + courseCode
                );
            }

            // 3. Prerequisite check
            if (!learner.meetsPrerequisite()) {
                return EnrollmentResult.failure(
                        EnrollmentStatus.PREREQUISITE_NOT_MET,
                        "Learner " + learnerId + " does not satisfy course prerequisites"
                );
            }

            // 4. Enroll or Waitlist based on capacity
            if (enrolledLearners.size() < capacity) {
                enrolledLearners.put(learnerId, learner);
                return EnrollmentResult.success(
                        EnrollmentStatus.ENROLLED,
                        "Learner " + learnerId + " successfully enrolled in " + courseCode
                );
            } else {
                waitlistLearners.put(learnerId, learner);
                return EnrollmentResult.success(
                        EnrollmentStatus.WAITLISTED,
                        "Course full. Learner " + learnerId + " placed on waitlist (Position: " + waitlistLearners.size() + ")"
                );
            }
        } finally {
            lock.unlock();
        }
    }

    public EnrollmentResult drop(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return EnrollmentResult.failure(EnrollmentStatus.INVALID_REQUEST, "Learner ID cannot be null or blank");
        }

        lock.lock();
        try {
            String trimmedId = learnerId.trim();

            // Check if learner is enrolled
            Learner removedLearner = enrolledLearners.remove(trimmedId);
            if (removedLearner == null) {
                // If not enrolled, check if they are in waitlist and remove them
                if (waitlistLearners.remove(trimmedId) != null) {
                    return EnrollmentResult.success(EnrollmentStatus.DROPPED, "Learner " + trimmedId + " removed from waitlist");
                }
                return EnrollmentResult.failure(EnrollmentStatus.NOT_FOUND, "Learner " + trimmedId + " is not enrolled or waitlisted");
            }

            // Seat opened; promote earliest eligible waitlisted learner (FIFO)
            Learner promotedLearner = promoteNextEligibleWaitlisted();

            return EnrollmentResult.dropSuccess(promotedLearner);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Traverses waitlist in FIFO order, skips and purges any ineligible learner,
     * and promotes the first eligible learner found.
     */
    private Learner promoteNextEligibleWaitlisted() {
        Iterator<Map.Entry<String, Learner>> iterator = waitlistLearners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Learner> entry = iterator.next();
            Learner candidate = entry.getValue();
            iterator.remove(); // Remove from waitlist

            if (candidate.meetsPrerequisite()) {
                enrolledLearners.put(candidate.getId(), candidate);
                return candidate;
            }
            // If ineligible, skipped and evicted from queue to avoid blocking
        }
        return null;
    }

    // Inspection getters (thread-safe snapshots)
    public int getCapacity() {
        return capacity;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public int getEnrolledCount() {
        lock.lock();
        try {
            return enrolledLearners.size();
        } finally {
            lock.unlock();
        }
    }

    public int getWaitlistCount() {
        lock.lock();
        try {
            return waitlistLearners.size();
        } finally {
            lock.unlock();
        }
    }

    public List<Learner> getEnrolledLearners() {
        lock.lock();
        try {
            return new ArrayList<>(enrolledLearners.values());
        } finally {
            lock.unlock();
        }
    }

    public List<Learner> getWaitlistLearners() {
        lock.lock();
        try {
            return new ArrayList<>(waitlistLearners.values());
        } finally {
            lock.unlock();
        }
    }
}
