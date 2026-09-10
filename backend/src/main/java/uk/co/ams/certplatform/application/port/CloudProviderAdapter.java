package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;

/**
 * Account-level operations for a cloud, independent of any one service.
 *
 * <p>Scanning is deliberately not here: it belongs to
 * {@link CertificateDiscoveryStrategy}, one implementation per service. An
 * adapter answers the questions that are about the account itself - can we
 * authenticate, and who are we authenticated as.
 *
 * <p><b>To onboard a cloud provider:</b> add a {@code CloudProviderType} value,
 * implement this interface, and add one strategy per service you want scanned.
 */
public interface CloudProviderAdapter {

    CloudProviderType providerType();

    ConnectionTestResult testConnection(Account account);
}
