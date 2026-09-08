package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.CloudProviderAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CloudProviderResolver {

    private final Map<CloudProviderType, CloudProviderAdapter> adapters;

    public CloudProviderResolver(List<CloudProviderAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(CloudProviderAdapter::providerType, Function.identity()));
    }

    public CloudProviderAdapter getAdapter(CloudProviderType type) {
        CloudProviderAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new UnsupportedOperationException("Provider type not supported: " + type);
        }
        return adapter;
    }
}
