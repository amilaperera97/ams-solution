import React, { useState } from 'react';
import Layout from '../components/Layout';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchApi, ApiError } from '../services/api';
import type { AccountAuthType, CloudProviderConfigMap } from '../types';

const AUTH_LABELS: Record<AccountAuthType, string> = {
  TOKEN: 'TOKEN',
  IAM_ROLE: 'IAM ROLE',
  ACCESS_KEY: 'ACCESS KEY',
};

// Mirrors the patterns AccountService validates with, so the operator is told what
// is wrong before the request is made rather than after the backend rejects it.
const AWS_ACCOUNT_ID = /^\d{12}$/;
const AWS_ROLE_ARN = /^arn:aws[a-z-]*:iam::\d{12}:role\/.+$/;
const AWS_REGION = /^[a-z]{2}(-[a-z]+){1,2}-\d$/;
const AWS_ACCESS_KEY_ID = /^(AKIA|ASIA)[A-Z0-9]{16}$/;

const emptyForm = {
  name: '',
  provider: '',
  environmentId: '',
  accountId: '',
  authType: 'IAM_ROLE' as AccountAuthType,
  token: '',
  roleArn: '',
  externalId: '',
  accessKeyId: '',
  secretAccessKey: '',
  region: '',
  status: 'Active',
};

const Accounts: React.FC = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [accountToEdit, setAccountToEdit] = useState<any | null>(null);
  const [accountToDelete, setAccountToDelete] = useState<any | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<{success: boolean, message: string} | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  // Whatever the API rejected the save with. Client-side validation mirrors the
  // backend rules but cannot know about them all - encryption not configured, an
  // environment that vanished - so the reason has to be shown, not swallowed.
  const [saveError, setSaveError] = useState<string | null>(null);

  const [formData, setFormData] = useState(emptyForm);

  const [selectedProviderId, setSelectedProviderId] = useState<string>('');
  const [selectedEnvironmentId, setSelectedEnvironmentId] = useState<string>('');

  const { data: org } = useQuery({
    queryKey: ['organization'],
    queryFn: () => fetchApi<any>('/api/v1/organisations/current'),
  });

  const { data: providers } = useQuery({
    queryKey: ['providers', org?.id],
    queryFn: () => fetchApi<any[]>(`/api/v1/organisations/${org?.id}/providers`),
    enabled: !!org?.id,
  });

  // Tells us whether AWS is wired to real AWS or to the simulator. TOKEN cannot reach
  // real AWS, so the form must not offer it once the backend is in REAL mode.
  const { data: providerConfig } = useQuery({
    queryKey: ['cloud-provider-config'],
    queryFn: () => fetchApi<CloudProviderConfigMap>('/api/v1/config/cloud-providers'),
    retry: false,
  });

  React.useEffect(() => {
    if (providers && providers.length > 0 && !selectedProviderId) {
      setSelectedProviderId(providers[0].id);
    }
  }, [providers, selectedProviderId]);

  const { data: environments } = useQuery({
    queryKey: ['environments', selectedProviderId],
    queryFn: () => fetchApi<any[]>(`/api/v1/providers/${selectedProviderId}/environments`),
    enabled: !!selectedProviderId,
  });

  React.useEffect(() => {
    if (environments && environments.length > 0) {
      if (!selectedEnvironmentId || !environments.find((e: any) => e.id === selectedEnvironmentId)) {
        setSelectedEnvironmentId(environments[0].id);
      }
    } else if (environments && environments.length === 0) {
      setSelectedEnvironmentId('');
    }
  }, [environments, selectedEnvironmentId]);

  // The page's list is filtered by selectedProviderId; the modal can point at a
  // different provider, and its environment select needs that provider's own list.
  const { data: formEnvironments } = useQuery({
    queryKey: ['environments', formData.provider],
    queryFn: () => fetchApi<any[]>(`/api/v1/providers/${formData.provider}/environments`),
    enabled: !!formData.provider,
  });

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts', selectedEnvironmentId],
    queryFn: () => fetchApi<any[]>(`/api/v1/environments/${selectedEnvironmentId}/accounts`),
    enabled: !!selectedEnvironmentId,
  });

  // The form's provider select holds a provider id; fall back to the value itself so a
  // literal type ("AWS") still resolves, which is how the older fixtures are shaped.
  const providerType: string = (
    providers?.find((p: any) => p.id === formData.provider)?.type || formData.provider || ''
  ).toString().toUpperCase();
  const isAws = providerType === 'AWS';
  const providerMode = providerConfig?.[providerType]?.mode ?? 'MOCK';
  // Real AWS: STS only understands signed requests, never a bearer token.
  const isRealAws = isAws && providerMode === 'REAL';
  const tokenAvailable = !isRealAws;

  // Which secrets the account already holds. They are never returned by the API, so
  // "already configured" is all the form can know - and all it needs, because leaving
  // a secret blank tells the backend to keep what it has.
  const storedRoleArn = !!accountToEdit?.roleArnConfigured;
  const storedExternalId = !!accountToEdit?.externalIdConfigured;
  const storedAccessKey = !!accountToEdit?.accessKeyId;
  const storedToken = accountToEdit?.authType === 'TOKEN' && !!accountToEdit?.credentialsConfigured;

  const saveAccMutation = useMutation({
    mutationFn: (acc: typeof emptyForm) => {
      // Only fields the operator actually filled in are sent. On an update an omitted
      // field means "leave the stored value alone"; on a create there is nothing to keep.
      const payload: Record<string, unknown> = {
        name: acc.name,
        accountId: acc.accountId,
        authType: acc.authType,
      };

      if (acc.authType === 'TOKEN') {
        payload.token = acc.token || undefined;
      } else if (acc.authType === 'IAM_ROLE') {
        payload.roleArn = acc.roleArn || undefined;
        payload.externalId = acc.externalId || undefined;
        payload.region = acc.region || undefined;
      } else if (acc.authType === 'ACCESS_KEY') {
        payload.accessKeyId = acc.accessKeyId || undefined;
        payload.secretAccessKey = acc.secretAccessKey || undefined;
        payload.region = acc.region || undefined;
      }

      if (accountToEdit) {
        return fetchApi(`/api/v1/accounts/${accountToEdit.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
      } else {
        return fetchApi(`/api/v1/environments/${acc.environmentId}/accounts`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      setSaveError(null);
      setIsModalOpen(false);
      setAccountToEdit(null);
      resetForm();
    },
    onError: (error: unknown) => {
      setSaveError(error instanceof ApiError || error instanceof Error
        ? error.message
        : 'Unable to save the account.');
    }
  });

  // The endpoint tests a *stored* account: it takes the internal account id and
  // reads the credentials back out of the database. There is nothing to test until
  // the account has been saved, which is why the button is disabled before then.
  const testConnectionMutation = useMutation({
    mutationFn: () => fetchApi<any>(`/api/v1/accounts/${accountToEdit?.id}/test-connection`, {
      method: 'POST'
    }),
    onSuccess: (data: any) => {
      // A credential AWS rejects is a 200 carrying status FAILED, so the HTTP
      // result says nothing about whether the connection worked.
      const connected = data?.status === 'CONNECTED';
      setConnectionStatus({
        success: connected,
        message: data?.message || (connected ? 'Connection successful' : 'Connection failed'),
      });
    },
    onError: (error: unknown) => {
      setConnectionStatus({
        success: false,
        message: error instanceof Error ? error.message : 'Connection failed',
      });
    }
  });

  const deleteAccMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/accounts/${id}`, {
      method: 'DELETE'
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      setIsDeleteModalOpen(false);
      setAccountToDelete(null);
      setDeleteError(null);
    },
    onError: () => {
      setDeleteError('Unable to delete account. Internal Server Error.');
    }
  });

  const resetForm = () => {
    setFormData({
      ...emptyForm,
      provider: selectedProviderId || '',
      environmentId: selectedEnvironmentId || '',
      authType: tokenAvailable ? emptyForm.authType : 'IAM_ROLE',
    });
    setConnectionStatus(null);
    setErrors({});
    setSaveError(null);
  };

  const validateForm = () => {
    const found: Record<string, string> = {};

    if (isAws && !AWS_ACCOUNT_ID.test(formData.accountId.trim())) {
      found.accountId = 'Account ID must contain exactly 12 digits.';
    } else if (!isAws && !formData.accountId.trim()) {
      found.accountId = 'Account ID is required.';
    }

    if (formData.authType === 'TOKEN') {
      if (!tokenAvailable) {
        found.authType = 'Token authentication cannot reach real AWS. Use IAM Role or Access Key.';
      }
      if (!formData.token.trim() && !storedToken) {
        found.token = 'Token is required when authentication is Token.';
      }
    }

    if (formData.authType === 'IAM_ROLE') {
      const roleArn = formData.roleArn.trim();
      if (!roleArn && !storedRoleArn) {
        found.roleArn = 'Role ARN is required when authentication is IAM Role.';
      } else if (roleArn && isAws && !AWS_ROLE_ARN.test(roleArn)) {
        found.roleArn = 'Role ARN must look like arn:aws:iam::123456789012:role/RoleName.';
      }
    }

    if (formData.authType === 'ACCESS_KEY') {
      const accessKeyId = formData.accessKeyId.trim();
      if (!accessKeyId && !storedAccessKey) {
        found.accessKeyId = 'Access key ID is required when authentication is Access Key.';
      } else if (accessKeyId && isAws && !AWS_ACCESS_KEY_ID.test(accessKeyId)) {
        found.accessKeyId = 'Access key ID must be 20 characters starting with AKIA or ASIA.';
      }
      if (!formData.secretAccessKey.trim() && !storedAccessKey) {
        found.secretAccessKey = 'Secret access key is required when authentication is Access Key.';
      }
    }

    if (formData.authType !== 'TOKEN') {
      const region = formData.region.trim();
      if (!region) {
        // Only REAL mode has to reach an actual regional endpoint.
        if (isRealAws) found.region = 'Region is required for real AWS accounts, e.g. eu-west-2.';
      } else if (isAws && !AWS_REGION.test(region)) {
        found.region = 'Region must look like an AWS region, e.g. eu-west-2.';
      }
    }

    setErrors(found);
    return Object.keys(found).length === 0;
  };

  const handleSaveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (validateForm()) {
      saveAccMutation.mutate(formData);
    }
  };

  const openAddModal = () => {
    setAccountToEdit(null);
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (acc: any) => {
    setAccountToEdit(acc);
    setFormData({
      ...emptyForm,
      name: acc.name || '',
      provider: acc.providerId || acc.provider || '',
      environmentId: acc.environmentId || acc.environment || '',
      accountId: acc.accountId || '',
      authType: (acc.authType as AccountAuthType) || 'IAM_ROLE',
      region: acc.region || '',
      status: acc.status || 'Active',
      // token, roleArn, externalId, accessKeyId and secretAccessKey stay blank: the API
      // does not return them, and blank means "keep what is stored".
    });
    setConnectionStatus(null);
    setErrors({});
    setSaveError(null);
    setIsModalOpen(true);
  };

  const openDeleteModal = (acc: any) => {
    setAccountToDelete(acc);
    setDeleteError(null);
    setIsDeleteModalOpen(true);
  };

  const field = (name: string) =>
    `w-full bg-gray-900 border ${errors[name] ? 'border-red-500' : 'border-gray-700'} rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white`;

  // Each message sits under the field it is about - the modal is short enough that a
  // separate summary would only repeat itself.
  const fieldError = (name: string) =>
    errors[name] ? <p role="alert" className="mt-1 text-sm text-red-400">{errors[name]}</p> : null;

  const keepBlankHint = (configured: boolean) =>
    configured ? <p className="mt-1 text-xs text-gray-400">Already configured. Leave blank to keep it.</p> : null;

  return (
    <Layout>
      <div className="bg-gray-900 border border-gray-700 rounded-xl shadow-lg p-6 h-full flex flex-col relative">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-4">
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <h2 className="text-2xl font-bold">Accounts</h2>
            
            <div className="flex space-x-2">
              {providers && providers.length > 0 && (
                <select
                  value={selectedProviderId}
                  onChange={e => setSelectedProviderId(e.target.value)}
                  className="bg-gray-800 border border-gray-700 rounded-md py-1.5 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {providers.map(p => (
                    <option key={p.id} value={p.id}>{p.name} ({p.type})</option>
                  ))}
                </select>
              )}

              {environments && environments.length > 0 && (
                <select
                  value={selectedEnvironmentId}
                  onChange={e => setSelectedEnvironmentId(e.target.value)}
                  className="bg-gray-800 border border-gray-700 rounded-md py-1.5 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {environments.map(e => (
                    <option key={e.id} value={e.id}>{e.name}</option>
                  ))}
                </select>
              )}
            </div>
          </div>
          <button 
            onClick={openAddModal}
            disabled={!selectedEnvironmentId}
            className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Add Account
          </button>
        </div>
        
        {isLoading ? (
          <div className="text-center text-gray-400 p-8">Loading accounts...</div>
        ) : accounts?.length === 0 ? (
          <div className="text-center text-gray-400 p-8 border-2 border-dashed border-gray-700 rounded-lg">
            No accounts configured. Click "Add Account" to start.
          </div>
        ) : (
          <div className="overflow-x-auto border border-gray-700 rounded-lg">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-gray-800 border-b border-gray-700">
                  <th className="p-3 text-sm font-semibold text-gray-300">Account Name</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Account ID</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Provider</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Environment</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Region</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Auth</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Status</th>
                  <th className="p-3 text-sm font-semibold text-gray-300 text-right">Certificates</th>
                  <th className="p-3 text-sm font-semibold text-gray-300 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {accounts?.map(acc => (
                  <tr key={acc.id} className="border-b border-gray-800 hover:bg-gray-800/50 transition-colors group">
                    <td className="p-3 text-sm font-medium">{acc.name}</td>
                    <td className="p-3 text-sm text-gray-400">{acc.accountId || '-'}</td>
                    <td className="p-3 text-sm text-gray-400">{acc.provider}</td>
                    <td className="p-3 text-sm text-gray-400">{acc.environmentId || acc.environment}</td>
                    <td className="p-3 text-sm text-gray-400">{acc.region || '-'}</td>
                    <td className="p-3 text-sm text-gray-400">{AUTH_LABELS[acc.authType as AccountAuthType] || acc.authType || '-'}</td>
                    <td className="p-3 text-sm">
                      <span className={`px-2 py-1 text-xs font-medium rounded-full ${
                        acc.status === 'Active' || acc.status === 'CONNECTED' ? 'bg-green-500/20 text-green-400' : 'bg-gray-500/20 text-gray-400'
                      }`}>
                        {acc.status}
                      </span>
                    </td>
                    <td className="p-3 text-sm text-right text-gray-400">{acc.certificatesCount || 0}</td>
                    <td className="p-3 text-sm text-right space-x-3">
                      <button onClick={() => openEditModal(acc)} className="text-gray-400 hover:text-white">Edit</button>
                      <button onClick={() => openDeleteModal(acc)} className="text-red-400 hover:text-red-300">Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {isModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50 overflow-y-auto">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md my-8">
              <h3 className="text-xl font-bold mb-4">{accountToEdit ? 'Edit Account' : 'Add Account'}</h3>
              
              <form onSubmit={handleSaveSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="cloudProvider" className="block text-sm font-medium mb-1">Cloud Provider</label>
                    <select
                      id="cloudProvider"
                      value={formData.provider}
                      onChange={e => {
                        setFormData({...formData, provider: e.target.value, environmentId: ''});
                      }}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                      disabled={!!accountToEdit}
                    >
                      <option value="" disabled>Select Provider</option>
                      {providers?.map(p => (
                        <option key={p.id} value={p.id}>{p.name} ({p.type})</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="environment" className="block text-sm font-medium mb-1">Environment</label>
                    <select
                      id="environment"
                      value={formData.environmentId}
                      onChange={e => setFormData({...formData, environmentId: e.target.value})}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                      disabled={!!accountToEdit || !formData.provider}
                    >
                      <option value="" disabled>Select Environment</option>
                      {formEnvironments?.map(e => (
                        <option key={e.id} value={e.id}>{e.name}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div>
                  <label htmlFor="accName" className="block text-sm font-medium mb-1">Account Name</label>
                  <input 
                    id="accName" 
                    type="text" 
                    required
                    value={formData.name}
                    onChange={e => setFormData({...formData, name: e.target.value})}
                    className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                    placeholder="e.g. Production Account UK"
                  />
                </div>
                
                <div>
                  <label htmlFor="accountId" className="block text-sm font-medium mb-1">Account ID</label>
                  <input 
                    id="accountId" 
                    type="text" 
                    required
                    value={formData.accountId}
                    onChange={e => setFormData({...formData, accountId: e.target.value})}
                    className={field('accountId')}
                    placeholder="123456789012"
                  />
                  {fieldError('accountId')}
                </div>

                <div className="pt-2 border-t border-gray-700">
                  <label className="block text-sm font-medium mb-2">Authentication Type</label>
                  <div className="flex space-x-6">
                    {tokenAvailable && (
                      <label className="flex items-center space-x-2 cursor-pointer">
                        <input 
                          type="radio" 
                          name="authType" 
                          value="TOKEN" 
                          checked={formData.authType === 'TOKEN'}
                          onChange={() => setFormData({...formData, authType: 'TOKEN'})}
                          className="text-blue-600 focus:ring-blue-500" 
                        />
                        <span>Token</span>
                      </label>
                    )}
                    <label className="flex items-center space-x-2 cursor-pointer">
                      <input 
                        type="radio" 
                        name="authType" 
                        value="IAM_ROLE" 
                        checked={formData.authType === 'IAM_ROLE'}
                        onChange={() => setFormData({...formData, authType: 'IAM_ROLE'})}
                        className="text-blue-600 focus:ring-blue-500" 
                      />
                      <span>IAM Role</span>
                    </label>
                    <label className="flex items-center space-x-2 cursor-pointer">
                      <input 
                        type="radio" 
                        name="authType" 
                        value="ACCESS_KEY" 
                        checked={formData.authType === 'ACCESS_KEY'}
                        onChange={() => setFormData({...formData, authType: 'ACCESS_KEY'})}
                        className="text-blue-600 focus:ring-blue-500" 
                      />
                      <span>Access Key</span>
                    </label>
                  </div>
                  {isRealAws && (
                    <p className="mt-2 text-xs text-gray-400">
                      This AWS provider is in REAL mode, so token authentication is not offered - AWS only
                      accepts signed requests. IAM Role is preferred; the backend assumes it with your local
                      AWS identity unless bootstrap access keys are stored.
                    </p>
                  )}
                  {fieldError('authType')}
                </div>

                {formData.authType === 'TOKEN' && (
                  <div className="pt-2">
                    <label htmlFor="token" className="block text-sm font-medium mb-1">Token Value</label>
                    <input 
                      id="token" 
                      type="password" 
                      value={formData.token}
                      onChange={e => setFormData({...formData, token: e.target.value})}
                      className={field('token')}
                      placeholder="••••••••••••••••"
                      autoComplete="new-password"
                    />
                    {fieldError('token')}
                    {keepBlankHint(storedToken)}
                  </div>
                )}

                {formData.authType === 'IAM_ROLE' && (
                  <>
                    <div className="pt-2">
                      <label htmlFor="roleArn" className="block text-sm font-medium mb-1">Role ARN</label>
                      <input 
                        id="roleArn" 
                        type="text" 
                        value={formData.roleArn}
                        onChange={e => setFormData({...formData, roleArn: e.target.value})}
                        className={field('roleArn')}
                        placeholder="arn:aws:iam::123456789012:role/RoleName"
                      />
                      {fieldError('roleArn')}
                      {keepBlankHint(storedRoleArn)}
                    </div>
                    <div>
                      <label htmlFor="externalId" className="block text-sm font-medium mb-1">
                        External ID <span className="text-gray-400 font-normal">(optional)</span>
                      </label>
                      <input 
                        id="externalId" 
                        type="text" 
                        value={formData.externalId}
                        onChange={e => setFormData({...formData, externalId: e.target.value})}
                        className={field('externalId')}
                        placeholder="Required only if the role's trust policy sets sts:ExternalId"
                      />
                      {keepBlankHint(storedExternalId)}
                    </div>
                  </>
                )}

                {formData.authType === 'ACCESS_KEY' && (
                  <>
                    <div className="pt-2">
                      <label htmlFor="accessKeyId" className="block text-sm font-medium mb-1">Access Key ID</label>
                      <input 
                        id="accessKeyId" 
                        type="text" 
                        value={formData.accessKeyId}
                        onChange={e => setFormData({...formData, accessKeyId: e.target.value})}
                        className={field('accessKeyId')}
                        placeholder="AKIAIOSFODNN7EXAMPLE"
                        autoComplete="off"
                      />
                      {fieldError('accessKeyId')}
                      {accountToEdit?.accessKeyId && (
                        <p className="mt-1 text-xs text-gray-400">
                          Currently {accountToEdit.accessKeyId}. Leave blank to keep it.
                        </p>
                      )}
                    </div>
                    <div>
                      <label htmlFor="secretAccessKey" className="block text-sm font-medium mb-1">Secret Access Key</label>
                      <input 
                        id="secretAccessKey" 
                        type="password" 
                        value={formData.secretAccessKey}
                        onChange={e => setFormData({...formData, secretAccessKey: e.target.value})}
                        className={field('secretAccessKey')}
                        placeholder="••••••••••••••••"
                        autoComplete="new-password"
                      />
                      {fieldError('secretAccessKey')}
                      {keepBlankHint(storedAccessKey)}
                    </div>
                  </>
                )}

                {formData.authType !== 'TOKEN' && (
                  <div>
                    <label htmlFor="region" className="block text-sm font-medium mb-1">
                      {isAws ? 'AWS Region' : 'Region'}
                    </label>
                    <input 
                      id="region" 
                      type="text" 
                      value={formData.region}
                      onChange={e => setFormData({...formData, region: e.target.value})}
                      className={field('region')}
                      placeholder="eu-west-2"
                    />
                    {fieldError('region')}
                  </div>
                )}

                {connectionStatus && (
                  <div role="alert" className={`p-3 rounded-md text-sm ${connectionStatus.success ? 'bg-green-500/20 text-green-400 border border-green-500/50' : 'bg-red-500/20 text-red-400 border border-red-500/50'}`}>
                    <p className="font-medium">
                      {connectionStatus.success ? '✓ Connection successful' : '✕ Connection failed'}
                    </p>
                    {/* STS says which identity answered, or why it refused - the only
                        detail that makes a failed test actionable. */}
                    <p className="mt-1 text-xs break-words opacity-90">{connectionStatus.message}</p>
                  </div>
                )}

                {saveError && (
                  <div role="alert" className="p-3 rounded-md text-sm bg-red-500/20 text-red-400 border border-red-500/50">
                    <p className="font-medium">✕ Account not saved</p>
                    <p className="mt-1 text-xs break-words opacity-90">{saveError}</p>
                  </div>
                )}

                <div className="flex space-x-3 pt-4 border-t border-gray-700 mt-6">
                  <button 
                    type="button"
                    onClick={() => setIsModalOpen(false)}
                    className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors"
                  >
                    Cancel
                  </button>
                  <button 
                    type="button"
                    onClick={() => testConnectionMutation.mutate()}
                    disabled={testConnectionMutation.isPending || !accountToEdit}
                    title={!accountToEdit ? 'Save the account first - the test reads the stored credentials.' : undefined}
                    className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {testConnectionMutation.isPending ? 'Testing...' : 'Test Connection'}
                  </button>
                  <button 
                    type="submit"
                    disabled={saveAccMutation.isPending}
                    className="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                  >
                    {saveAccMutation.isPending ? 'Saving...' : 'Save Account'}
                  </button>
                </div>
                {!accountToEdit && (
                  <p className="mt-2 text-xs text-gray-400">
                    Save the account first, then reopen it with Edit to test the connection - the test
                    reads the credentials back from the stored account.
                  </p>
                )}
              </form>
            </div>
          </div>
        )}

        {isDeleteModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md" role="dialog" aria-modal="true">
              <h3 className="text-xl font-bold text-red-500 mb-4">Delete Account?</h3>
              {deleteError && (
                <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-md text-red-400 text-sm" role="alert">
                  {deleteError}
                </div>
              )}
              
              <div className="mb-6">
                <p className="text-gray-300 mb-2">
                  Are you sure you want to delete this account: <strong>{accountToDelete?.name}</strong>?
                </p>
                {accountToDelete?.certificatesCount > 0 && (
                  <div className="p-4 bg-yellow-500/20 border border-yellow-500 rounded-md mt-4">
                    <p className="text-yellow-400 font-medium text-sm">Warning: Certificates Found</p>
                    <p className="text-gray-300 text-xs mt-1">
                      {accountToDelete.certificatesCount} certificates are associated with this account. The certificate records will remain available in history, but active scanning will cease.
                    </p>
                  </div>
                )}
              </div>
              
              <div className="flex space-x-3">
                <button 
                  type="button"
                  onClick={() => setIsDeleteModalOpen(false)}
                  className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors"
                >
                  Cancel
                </button>
                <button 
                  type="button"
                  onClick={() => deleteAccMutation.mutate(accountToDelete?.id)}
                  disabled={deleteAccMutation.isPending}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                >
                  {deleteAccMutation.isPending ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
};

export default Accounts;
