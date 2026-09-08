package uk.co.ams.certplatform.application.port;

import java.util.concurrent.Callable;

public interface RetryPolicy {
    <T> T execute(Callable<T> callable) throws Exception;
}
