package com.example.certplatform.application.port;

import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.model.Account;
import com.example.certplatform.domain.model.CertificateScanResult;
import com.example.certplatform.domain.model.ConnectionTestResult;
import com.example.certplatform.domain.model.ScanContext;

public interface CloudProviderAdapter {
    CloudProviderType providerType();
    
    ConnectionTestResult testConnection(Account account);
    
    CertificateScanResult scanCertificates(ScanContext context);
}
