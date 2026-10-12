package com.codearena.codearena.entity;

public enum SubmissionStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    ACCEPTED("AC"),
    WRONG_ANSWER("WA"),
    COMPILE_ERROR("CE"),
    RUNTIME_ERROR("RE"),
    TIME_LIMIT_EXCEEDED("TLE"),
    SYSTEM_ERROR("SE");

    private final String code;

    SubmissionStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean canTransitionTo(SubmissionStatus next) {
        if (next == null) {
            return false;
        }
        if (this == PENDING) {
            return next == RUNNING;
        }
        return this == RUNNING && next != PENDING && next != RUNNING;
    }

    public static SubmissionStatus fromCode(String code) {
        for (SubmissionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code) || status.name().equalsIgnoreCase(code)) {
                return status;
            }
        }
        if ("SUBMITTED".equalsIgnoreCase(code)) {
            return PENDING;
        }
        return null;
    }
}
