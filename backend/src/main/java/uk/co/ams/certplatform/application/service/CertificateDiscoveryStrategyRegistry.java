package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CertificateDiscoveryStrategyRegistry {

    private final List<CertificateDiscoveryStrategy> strategies;

    public CertificateDiscoveryStrategyRegistry(List<CertificateDiscoveryStrategy> strategies) {
        this.strategies = strategies;
    }

    public List<CertificateDiscoveryStrategy> getAllSupportedStrategies(CloudProviderType provider) {
        return strategies.stream()
                .filter(s -> s.capability().getProvider() == provider)
                .collect(Collectors.toList());
    }

    public List<CertificateDiscoveryStrategy> findStrategies(CloudProviderType provider, String service, String os) {
        return strategies.stream()
                .filter(s -> s.capability().getProvider() == provider)
                .filter(s -> service == null || service.equalsIgnoreCase(s.capability().getService()))
                .filter(s -> os == null || os.equalsIgnoreCase(s.capability().getOperatingSystemSupport()))
                .collect(Collectors.toList());
    }
}
