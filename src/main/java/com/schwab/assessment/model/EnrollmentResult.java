package com.schwab.assessment.model;

import java.util.Optional;

public final class EnrollmentResult {
    private final boolean success;
    private final EnrollmentStatus status;
    private final String message;
    private final Learner promotedLearner;

    public EnrollmentResult(boolean success, EnrollmentStatus status, String message, Learner promotedLearner) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.promotedLearner = promotedLearner;
    }

    public static EnrollmentResult success(EnrollmentStatus status, String message) {
        return new EnrollmentResult(true, status, message, null);
    }

    public static EnrollmentResult dropSuccess(Learner promotedLearner) {
        String msg = (promotedLearner != null)
                ? "Dropped successfully. Learner " + promotedLearner.getId() + " promoted from waitlist."
                : "Dropped successfully. No eligible waitlisted learner to promote.";
        return new EnrollmentResult(true, EnrollmentStatus.DROPPED, msg, promotedLearner);
    }

    public static EnrollmentResult failure(EnrollmentStatus status, String message) {
        return new EnrollmentResult(false, status, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Optional<Learner> getPromotedLearner() {
        return Optional.ofNullable(promotedLearner);
    }

    @Override
    public String toString() {
        return "EnrollmentResult{" +
                "success=" + success +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", promotedLearner=" + (promotedLearner != null ? promotedLearner.getId() : "none") +
                '}';
    }
}
