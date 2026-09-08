package uk.co.ams.certplatform.application.port;

public interface ScanJobPublisher {
    void publish(String scanId);
}
