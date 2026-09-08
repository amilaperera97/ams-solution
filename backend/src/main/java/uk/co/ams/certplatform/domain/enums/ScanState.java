package com.example.certplatform.domain.enums;

public enum ScanState {
    REQUESTED,
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIAL_SUCCESS,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED
}
