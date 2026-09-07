package com.example.certplatform.application.service;

import com.example.certplatform.application.port.CloudProviderAdapter;
import com.example.certplatform.domain.enums.CloudProviderType;
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
