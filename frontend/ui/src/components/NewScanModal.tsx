import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchApi } from '../services/api';
import { useDiscoveryCapabilities } from '../hooks/useApi';
import type { DiscoveryCapability } from '../types';

interface NewScanModalProps {
  onClose: () => void;
  onSave: (scan: any) => void;
  isSaving: boolean;
  initialScan?: any;
}

const NewScanModal: React.FC<NewScanModalProps> = ({ onClose, onSave, isSaving, initialScan }) => {
  const [step, setStep] = useState<'CONFIGURE' | 'REVIEW'>('CONFIGURE');
  
  const [name, setName] = useState(initialScan?.name || '');
  const [scopeType, setScopeType] = useState<string>(initialScan?.scopeType || '');
  
  const [selectedProviders, setSelectedProviders] = useState<string[]>(initialScan?.providers || []);
  const [selectedEnvironments, setSelectedEnvironments] = useState<string[]>(initialScan?.environments || []);
  const [selectedAccounts, setSelectedAccounts] = useState<string[]>(initialScan?.accounts || []);
  
  const [regions, setRegions] = useState<string[]>(initialScan?.regions || []);
  const [services, setServices] = useState<string[]>(initialScan?.services || []);
  const [certTypes, setCertTypes] = useState<string[]>(initialScan?.certTypes || []);

  const { data: org } = useQuery({
    queryKey: ['organization'],
    queryFn: () => fetchApi<any>('/api/v1/organisations/current')
  });

  // The scannable services come from the backend catalogue rather than a
  // hardcoded list, so a newly onboarded service appears here with no UI change.
  const { data: capabilities, isLoading: capabilitiesLoading } = useDiscoveryCapabilities();

  const capabilitiesByPhase = React.useMemo(() => {
    const grouped = new Map<number, DiscoveryCapability[]>();
    (capabilities ?? []).forEach(capability => {
      const bucket = grouped.get(capability.phase) ?? [];
      bucket.push(capability);
      grouped.set(capability.phase, bucket);
    });
    return Array.from(grouped.entries()).sort(([a], [b]) => a - b);
  }, [capabilities]);

  const labelForService = (key: string) =>
    (capabilities ?? []).find(capability => capability.key === key)?.label ?? key;

  const { data: allProviders } = useQuery({
    queryKey: ['providers', org?.id],
    queryFn: () => fetchApi<any[]>(`/api/v1/organisations/${org?.id}/providers`),
    enabled: !!org?.id
  });

  const { data: allEnvironments } = useQuery({
    queryKey: ['environments', selectedProviders],
    queryFn: async () => {
      if (selectedProviders.length === 0 || selectedProviders.includes('all')) return [];
      const envPromises = selectedProviders.map(p => fetchApi<any[]>(`/api/v1/providers/${p}/environments`).catch(() => []));
      const results = await Promise.all(envPromises);
      const flattened = results.flat();
      const unique = Array.from(new Map(flattened.map(item => [item.id, item])).values());
      return unique;
    },
    enabled: selectedProviders.length > 0 && !selectedProviders.includes('all')
  });

  const { data: allAccounts } = useQuery({
    queryKey: ['accounts', selectedEnvironments],
    queryFn: async () => {
      if (selectedEnvironments.length === 0 || selectedEnvironments.includes('all')) return [];
      const accPromises = selectedEnvironments.map(e => fetchApi<any[]>(`/api/v1/environments/${e}/accounts`).catch(() => []));
      const results = await Promise.all(accPromises);
      const flattened = results.flat();
      const unique = Array.from(new Map(flattened.map(item => [item.id, item])).values());
      return unique;
    },
    enabled: selectedEnvironments.length > 0 && !selectedEnvironments.includes('all')
  });

  // Clear invalid selections when parents change
  useEffect(() => {
    if (scopeType !== 'CUSTOM') {
      if (allEnvironments && selectedEnvironments.length > 0 && !selectedEnvironments.includes('all')) {
        const validEnvIds = allEnvironments.map((e: any) => e.id);
        const newSelectedEnvs = selectedEnvironments.filter(e => validEnvIds.includes(e));
        if (newSelectedEnvs.length !== selectedEnvironments.length) {
          setSelectedEnvironments(newSelectedEnvs);
        }
      }
    }
  }, [allEnvironments, scopeType]);

  useEffect(() => {
    if (scopeType !== 'CUSTOM') {
      if (allAccounts && selectedAccounts.length > 0 && !selectedAccounts.includes('all')) {
        const validAccIds = allAccounts.map((a: any) => a.id);
        const newSelectedAccs = selectedAccounts.filter(a => validAccIds.includes(a));
        if (newSelectedAccs.length !== selectedAccounts.length) {
          setSelectedAccounts(newSelectedAccs);
        }
      }
    }
  }, [allAccounts, scopeType]);

  // A saved scan being re-run may name a service that has since been disabled or
  // renamed; silently sending it would fail the new scan on an unknown service.
  useEffect(() => {
    if (!capabilities) return;
    const selectable = new Set(capabilities.filter(c => c.selectable).map(c => c.key));
    setServices(current => {
      const kept = current.filter(key => selectable.has(key));
      return kept.length === current.length ? current : kept;
    });
  }, [capabilities]);

  console.log("Render State:", { scopeType, selectedProviders, selectedEnvironments, selectedAccounts });
  const handleProviderChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = Array.from(e.target.selectedOptions, option => option.value);
    if (val.includes('all')) {
      if (selectedProviders.includes('all') && val.length > 1) {
        setSelectedProviders(val.filter(v => v !== 'all'));
      } else {
        setSelectedProviders(['all']);
      }
    } else {
      setSelectedProviders(val);
    }
    // Change provider resets env and acc if not custom
    if (scopeType !== 'CUSTOM') {
      setSelectedEnvironments([]);
      setSelectedAccounts([]);
    }
  };

  const handleEnvironmentChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = Array.from(e.target.selectedOptions, option => option.value);
    if (val.includes('all')) {
      if (selectedEnvironments.includes('all') && val.length > 1) {
        setSelectedEnvironments(val.filter(v => v !== 'all'));
      } else {
        setSelectedEnvironments(['all']);
      }
    } else {
      setSelectedEnvironments(val);
    }
    if (scopeType !== 'CUSTOM') {
      setSelectedAccounts([]);
    }
  };

  const handleAccountChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = Array.from(e.target.selectedOptions, option => option.value);
    setSelectedAccounts(val);
  };

  const handleCheckboxChange = (setState: React.Dispatch<React.SetStateAction<string[]>>, state: string[], val: string, checked: boolean) => {
    if (checked) {
      setState([...state, val]);
    } else {
      setState(state.filter(item => item !== val));
    }
  };

  const canReview = () => {
    if (!name) return false;
    if (!scopeType) return false;
    if (scopeType === 'PROVIDER' && selectedProviders.length === 0) return false;
    if (scopeType === 'ENVIRONMENT' && (selectedProviders.length === 0 || selectedEnvironments.length === 0)) return false;
    if (scopeType === 'ACCOUNT' && (selectedProviders.length === 0 || selectedEnvironments.length === 0 || selectedAccounts.length === 0)) return false;
    if (scopeType === 'CUSTOM' && (selectedProviders.length === 0 && selectedEnvironments.length === 0 && selectedAccounts.length === 0)) return false;
    return true;
  };

  const getScopeSummary = () => {
    let summary = '';
    if (scopeType === 'PROVIDER') {
      if (selectedProviders.includes('all')) summary = 'All configured cloud providers';
      else summary = `All ${selectedProviders.join(', ').toUpperCase()} accounts`;
    } else if (scopeType === 'ENVIRONMENT') {
      if (selectedEnvironments.includes('all')) summary = `All accounts in all environments`;
      else summary = `All accounts in ${selectedEnvironments.join(', ').toUpperCase()}`;
    } else if (scopeType === 'ACCOUNT') {
      summary = `${selectedAccounts.length} accounts selected`;
    } else if (scopeType === 'CUSTOM') {
      summary = `${selectedAccounts.length} accounts selected`;
    }
    return summary;
  };

  const handleStartScan = () => {
    const payload = {
      name,
      scopeType,
      providerIds: selectedProviders,
      environmentIds: selectedEnvironments,
      accountIds: selectedAccounts,
      regions,
      services
    };
    onSave(payload);
  };

  return (
    <div className="absolute inset-0 bg-black/60 flex items-center justify-center p-4 z-50 overflow-y-auto">
      <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-2xl my-8">
        <h3 className="text-xl font-bold mb-6">{step === 'CONFIGURE' ? (initialScan ? 'Run Scan Again' : 'Create New Scan') : 'Review Scan'}</h3>
        
        {step === 'CONFIGURE' && (
          <div className="space-y-6">
            <div>
              <label htmlFor="scanName" className="block text-sm font-medium mb-1 text-gray-300">Scan Name</label>
              <input 
                id="scanName" 
                type="text" 
                value={name}
                onChange={e => setName(e.target.value)}
                className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                placeholder="e.g. Production Certificate Scan"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-3 text-gray-300">What do you want to scan?</label>
              <div className="space-y-2">
                {['PROVIDER', 'ENVIRONMENT', 'ACCOUNT', 'CUSTOM'].map(type => (
                  <label key={type} className="flex items-center space-x-3 cursor-pointer">
                    <input 
                      type="radio" 
                      name="scopeType" 
                      value={type}
                      checked={scopeType === type}
                      onChange={() => {
                        setScopeType(type);
                        if (!initialScan || type !== initialScan.scopeType) {
                          setSelectedProviders([]);
                          setSelectedEnvironments([]);
                          setSelectedAccounts([]);
                        }
                      }}
                      className="text-blue-500 focus:ring-blue-500 bg-gray-900 border-gray-700"
                    />
                    <span className="text-gray-300">{type === 'PROVIDER' ? 'Scan by Cloud Provider' : type === 'ENVIRONMENT' ? 'Scan by Environment' : type === 'ACCOUNT' ? 'Scan by Account' : 'Custom Scan'}</span>
                  </label>
                ))}
              </div>
            </div>

            {scopeType && (
              <div className="p-4 bg-gray-900/50 border border-gray-700 rounded-lg space-y-4">
                
                {(scopeType === 'PROVIDER' || scopeType === 'ENVIRONMENT' || scopeType === 'ACCOUNT' || scopeType === 'CUSTOM') && (
                  <div>
                    <label htmlFor="providerSelect" className="block text-sm font-medium mb-1 text-gray-400">Provider</label>
                    <select
                      id="providerSelect"
                      multiple={scopeType === 'PROVIDER' || scopeType === 'CUSTOM'}
                      value={scopeType === 'PROVIDER' || scopeType === 'CUSTOM' ? selectedProviders : selectedProviders[0] || ''}
                      onChange={handleProviderChange}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 text-white h-auto"
                      aria-label="Provider"
                    >
                      {scopeType !== 'PROVIDER' && scopeType !== 'CUSTOM' && <option value="" disabled>Select Provider</option>}
                      {(scopeType === 'PROVIDER' || scopeType === 'CUSTOM') && <option value="all">All Providers</option>}
                      {allProviders?.map((p: any) => (
                        <option key={p.id} value={p.id}>{p.name}</option>
                      ))}
                    </select>
                  </div>
                )}

                {(scopeType === 'ENVIRONMENT' || scopeType === 'ACCOUNT' || scopeType === 'CUSTOM') && (
                  <div>
                    <label htmlFor="environmentSelect" className="block text-sm font-medium mb-1 text-gray-400">Environment</label>
                    <select
                      id="environmentSelect"
                      multiple={scopeType === 'ENVIRONMENT' || scopeType === 'CUSTOM'}
                      value={scopeType === 'ENVIRONMENT' || scopeType === 'CUSTOM' ? selectedEnvironments : selectedEnvironments[0] || ''}
                      onChange={handleEnvironmentChange}
                      disabled={selectedProviders.length === 0}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 text-white h-auto"
                      aria-label="Environment"
                    >
                      {scopeType !== 'ENVIRONMENT' && scopeType !== 'CUSTOM' && <option value="" disabled>Select Environment</option>}
                      {(scopeType === 'ENVIRONMENT' || scopeType === 'CUSTOM') && <option value="all">All Environments</option>}
                      {allEnvironments?.map((e: any) => (
                        <option key={e.id} value={e.id}>{e.name}</option>
                      ))}
                    </select>
                  </div>
                )}

                {(scopeType === 'ACCOUNT' || scopeType === 'CUSTOM') && (
                  <div>
                    <label htmlFor="accountSelect" className="block text-sm font-medium mb-1 text-gray-400">Account</label>
                    <select
                      id="accountSelect"
                      multiple
                      value={selectedAccounts}
                      onChange={handleAccountChange}
                      disabled={selectedEnvironments.length === 0 && scopeType !== 'CUSTOM'}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 text-white h-32"
                      aria-label="Account"
                    >
                      {allAccounts?.map((a: any) => (
                        <option key={a.id} value={a.id}>{a.name} ({a.accountId})</option>
                      ))}
                    </select>
                    {selectedAccounts.length === 1 && allAccounts?.find((a: any) => a.id === selectedAccounts[0]) && (
                      <div className="mt-2 text-xs text-gray-400">
                        {allAccounts.find((a: any) => a.id === selectedAccounts[0]).accountId} - 
                        {allAccounts.find((a: any) => a.id === selectedAccounts[0]).name}
                      </div>
                    )}
                  </div>
                )}
                
                <div className="pt-2 text-sm text-blue-400 font-medium">
                  {getScopeSummary()}
                </div>
              </div>
            )}

            {scopeType && (
              <div className="pt-4 border-t border-gray-700">
                <h4 className="text-sm font-bold text-gray-300 mb-3">Discovery Options</h4>
                
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <span className="block text-xs font-semibold text-gray-500 mb-2">Regions</span>
                    {['eu-west-1', 'eu-west-2', 'us-east-1'].map(region => (
                      <label key={region} className="flex items-center space-x-2 mb-1">
                        <input type="checkbox" checked={regions.includes(region)} onChange={e => handleCheckboxChange(setRegions, regions, region, e.target.checked)} className="bg-gray-900 border-gray-700 rounded" />
                        <span className="text-sm text-gray-300">{region}</span>
                      </label>
                    ))}
                  </div>
                  <div>
                    <span className="block text-xs font-semibold text-gray-500 mb-2">Services</span>
                    {capabilitiesLoading && (
                      <span className="text-sm text-gray-500">Loading available services...</span>
                    )}
                    {!capabilitiesLoading && capabilitiesByPhase.length === 0 && (
                      <span className="text-sm text-gray-500">No discovery services are registered.</span>
                    )}
                    <div className="max-h-56 overflow-y-auto pr-1 space-y-3">
                      {capabilitiesByPhase.map(([phase, group]) => (
                        <div key={phase}>
                          <span className="block text-[10px] uppercase tracking-wide text-gray-600 mb-1">
                            Phase {phase}
                          </span>
                          {group.map(capability => (
                            <label
                              key={`${capability.provider}:${capability.key}`}
                              title={capability.selectable
                                ? capability.requiredPermissions.join(', ')
                                : capability.disabled
                                  ? 'Switched off by configuration'
                                  : 'Not implemented yet'}
                              className={`flex items-center space-x-2 mb-1 ${
                                capability.selectable ? 'cursor-pointer' : 'cursor-not-allowed opacity-40'}`}
                            >
                              <input
                                type="checkbox"
                                disabled={!capability.selectable}
                                checked={services.includes(capability.key)}
                                onChange={e => handleCheckboxChange(setServices, services, capability.key, e.target.checked)}
                                className="bg-gray-900 border-gray-700 rounded disabled:opacity-40"
                              />
                              <span className="text-sm text-gray-300">{capability.label}</span>
                              {capability.scope === 'GLOBAL' && (
                                <span
                                  title="Global service - scanned once per account, not once per region"
                                  className="text-[10px] text-gray-500 border border-gray-700 rounded px-1"
                                >
                                  global
                                </span>
                              )}
                            </label>
                          ))}
                        </div>
                      ))}
                    </div>
                    <span className="block text-xs text-gray-600 mt-2">
                      Leave empty to scan every available service.
                    </span>
                  </div>
                </div>
              </div>
            )}
            
            <div className="flex space-x-3 pt-4">
              <button 
                type="button"
                onClick={onClose}
                className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors"
              >
                Cancel
              </button>
              <button 
                type="button"
                onClick={() => setStep('REVIEW')}
                disabled={!canReview()}
                className="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
              >
                Review Scan
              </button>
            </div>
          </div>
        )}

        {step === 'REVIEW' && (
          <div className="space-y-6">
            <div className="bg-gray-900 p-4 rounded-lg border border-gray-700 space-y-4">
              <div>
                <span className="block text-xs font-semibold text-gray-500 mb-1">Scan Name</span>
                <span className="text-sm text-white font-medium">{name}</span>
              </div>
              
              <div>
                <span className="block text-xs font-semibold text-gray-500 mb-1">Provider</span>
                <span className="text-sm text-white">{selectedProviders.includes('all') ? 'All Providers' : selectedProviders.map(p => allProviders?.find((ap: any) => ap.id === p)?.name || p).join(', ').toUpperCase()}</span>
              </div>
              
              {(scopeType === 'ENVIRONMENT' || scopeType === 'ACCOUNT' || scopeType === 'CUSTOM') && (
                <div>
                  <span className="block text-xs font-semibold text-gray-500 mb-1">Environment</span>
                  <span className="text-sm text-white">{selectedEnvironments.includes('all') ? 'All Environments' : selectedEnvironments.map(e => allEnvironments?.find((ae: any) => ae.id === e)?.name || e).join(', ').toUpperCase()}</span>
                </div>
              )}
              
              {(scopeType === 'ACCOUNT' || scopeType === 'CUSTOM') && (
                <div>
                  <span className="block text-xs font-semibold text-gray-500 mb-1">Accounts</span>
                  <div className="text-sm text-white space-y-1">
                    {selectedAccounts.map(accId => {
                      const acc = allAccounts?.find((a: any) => a.id === accId);
                      return <div key={accId}>{acc ? `${acc.name} (${acc.accountId})` : accId}</div>;
                    })}
                  </div>
                </div>
              )}

              {regions.length > 0 && (
                <div>
                  <span className="block text-xs font-semibold text-gray-500 mb-1">Regions</span>
                  <span className="text-sm text-white">{regions.join(', ')}</span>
                </div>
              )}

              {services.length > 0 && (
                <div>
                  <span className="block text-xs font-semibold text-gray-500 mb-1">Services</span>
                  <span className="text-sm text-white">{services.map(labelForService).join(', ')}</span>
                </div>
              )}
            </div>

            <div className="flex space-x-3 pt-4">
              <button 
                type="button"
                onClick={() => setStep('CONFIGURE')}
                className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors"
              >
                Back
              </button>
              <button 
                type="button"
                onClick={handleStartScan}
                disabled={isSaving}
                className="flex-1 bg-green-600 hover:bg-green-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
              >
                {isSaving ? 'Starting...' : 'Start Scan'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default NewScanModal;
