package com.example.certplatform.application.port;

public interface ScanJobPublisher {
    void publish(String scanId);
}
