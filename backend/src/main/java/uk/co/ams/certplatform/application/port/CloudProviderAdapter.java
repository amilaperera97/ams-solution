package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.CertificateScanResult;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.domain.model.ScanContext;

public interface CloudProviderAdapter {
    CloudProviderType providerType();
    
    ConnectionTestResult testConnection(Account account);
    
    CertificateScanResult scanCertificates(ScanContext context);
}
