import React, { useState } from 'react';
import Layout from '../components/Layout';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchApi } from '../services/api';

const Accounts: React.FC = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [accountToEdit, setAccountToEdit] = useState<any | null>(null);
  const [accountToDelete, setAccountToDelete] = useState<any | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<{success: boolean, message: string} | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const [formData, setFormData] = useState({ 
    name: '', 
    provider: 'AWS', 
    environmentId: 'env-prod', 
    accountId: '',
    authType: 'TOKEN',
    token: '',
    roleArn: '',
    status: 'Active' 
  });

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

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts', selectedEnvironmentId],
    queryFn: () => fetchApi<any[]>(`/api/v1/environments/${selectedEnvironmentId}/accounts`),
    enabled: !!selectedEnvironmentId,
  });



  const saveAccMutation = useMutation({
    mutationFn: (acc: any) => {
      const payload = { 
        name: acc.name,
        accountId: acc.accountId,
        authType: acc.authType,
        token: acc.authType === 'TOKEN' ? acc.token : undefined,
        roleArn: acc.authType === 'IAM_ROLE' ? acc.roleArn : undefined
      };

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
      setIsModalOpen(false);
      setAccountToEdit(null);
      resetForm();
    }
  });

  const testConnectionMutation = useMutation({
    mutationFn: () => fetchApi(`/api/v1/accounts/${accountToEdit?.id || formData.accountId || 'test'}/test-connection`, {
      method: 'POST'
    }),
    onSuccess: (data: any) => {
      setConnectionStatus({ success: true, message: data?.message || 'Connection successful' });
    },
    onError: () => {
      setConnectionStatus({ success: false, message: 'Connection failed' });
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
      name: '', 
      provider: selectedProviderId || '', 
      environmentId: selectedEnvironmentId || '', 
      accountId: '',
      authType: 'TOKEN',
      token: '',
      roleArn: '',
      status: 'Active' 
    });
    setConnectionStatus(null);
    setValidationError(null);
  };

  const validateForm = () => {
    if (formData.provider === 'AWS') {
      if (!/^\d{12}$/.test(formData.accountId)) {
        setValidationError('Account ID must contain exactly 12 digits.');
        return false;
      }
    }
    setValidationError(null);
    return true;
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
      name: acc.name || '', 
      provider: acc.provider || 'AWS', 
      environmentId: acc.environmentId || acc.environment || 'env-prod', 
      accountId: acc.accountId || '',
      authType: acc.authType || 'TOKEN',
      token: acc.token || '',
      roleArn: acc.roleArn || acc.connectionDetails?.roleArn || '',
      status: acc.status || 'Active' 
    });
    setConnectionStatus(null);
    setValidationError(null);
    setIsModalOpen(true);
  };

  const openDeleteModal = (acc: any) => {
    setAccountToDelete(acc);
    setDeleteError(null);
    setIsDeleteModalOpen(true);
  };

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
                    <td className="p-3 text-sm text-gray-400">{acc.authType === 'IAM_ROLE' ? 'IAM ROLE' : 'TOKEN'}</td>
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
              
              {validationError && (
                <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-md text-red-400 text-sm">
                  {validationError}
                </div>
              )}

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
                      {/* Only showing environments for the currently selected provider in the main view for simplicity in this demo */}
                      {formData.provider === selectedProviderId && environments?.map(e => (
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
                    className={`w-full bg-gray-900 border ${validationError ? 'border-red-500' : 'border-gray-700'} rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white`}
                    placeholder="123456789012"
                  />
                </div>

                <div className="pt-2 border-t border-gray-700">
                  <label className="block text-sm font-medium mb-2">Authentication Type</label>
                  <div className="flex space-x-6">
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
                  </div>
                </div>

                {formData.authType === 'TOKEN' ? (
                  <div className="pt-2">
                    <label htmlFor="token" className="block text-sm font-medium mb-1">Token Value</label>
                    <input 
                      id="token" 
                      type="password" 
                      required
                      value={formData.token}
                      onChange={e => setFormData({...formData, token: e.target.value})}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                      placeholder="••••••••••••••••"
                    />
                  </div>
                ) : (
                  <div className="pt-2">
                    <label htmlFor="roleArn" className="block text-sm font-medium mb-1">Role ARN</label>
                    <input 
                      id="roleArn" 
                      type="text" 
                      required
                      value={formData.roleArn}
                      onChange={e => setFormData({...formData, roleArn: e.target.value})}
                      className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                      placeholder="arn:aws:iam::123456789012:role/RoleName"
                    />
                  </div>
                )}

                {connectionStatus && (
                  <div className={`p-3 rounded-md text-sm ${connectionStatus.success ? 'bg-green-500/20 text-green-400 border border-green-500/50' : 'bg-red-500/20 text-red-400 border border-red-500/50'}`}>
                    {connectionStatus.success ? '✓ Connection successful' : '✕ Connection failed'}
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
                    disabled={testConnectionMutation.isPending}
                    className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
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
