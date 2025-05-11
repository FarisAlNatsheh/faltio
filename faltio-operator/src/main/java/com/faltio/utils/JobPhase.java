package com.faltio.utils;

import io.fabric8.kubernetes.api.model.batch.v1.Job;

public enum JobPhase {
    SUCCEEDED,
    FAILED,
    RUNNING,
    PENDING;

    public static JobPhase fromJob(Job job) {
        if (job == null || job.getStatus() == null) {
            return PENDING;
        }

        if (job.getStatus().getFailed() != null && job.getStatus().getFailed() > 0) {
            return FAILED;
        }

        if (job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0) {
            return SUCCEEDED;
        }

        return RUNNING;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
