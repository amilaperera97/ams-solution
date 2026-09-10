import React, { useState } from 'react';
import Layout from '../components/Layout';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchApi } from '../services/api';

const Providers: React.FC = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [providerToEdit, setProviderToEdit] = useState<any | null>(null);
  const [providerToDelete, setProviderToDelete] = useState<any | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [formData, setFormData] = useState({ name: '', type: 'AWS' });

  const { data: org } = useQuery({
    queryKey: ['organization'],
    queryFn: () => fetchApi<any>('/api/v1/organisations/current'),
  });

  const { data: providers, isLoading } = useQuery({
    queryKey: ['providers', org?.id],
    queryFn: () => fetchApi<any[]>(`/api/v1/organisations/${org?.id}/providers`),
    enabled: !!org?.id
  });

  const saveProviderMutation = useMutation({
    mutationFn: (provider: any) => {
      if (providerToEdit) {
        return fetchApi(`/api/v1/providers/${providerToEdit.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(provider)
        });
      } else {
        return fetchApi(`/api/v1/organisations/${org?.id}/providers`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: provider.name, type: provider.type })
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      setIsModalOpen(false);
      setProviderToEdit(null);
      setFormData({ name: '', type: 'AWS' });
    }
  });

  const deleteProviderMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/providers/${id}`, {
      method: 'DELETE'
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
      setIsDeleteModalOpen(false);
      setProviderToDelete(null);
      setDeleteError(null);
    },
    onError: () => {
      setDeleteError('Unable to delete provider. Internal Server Error.');
    }
  });

  const testConnectionMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/providers/${id}/test-connection`, {
      method: 'POST'
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['providers'] });
    }
  });

  const handleSaveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveProviderMutation.mutate(formData);
  };

  const openAddModal = () => {
    setProviderToEdit(null);
    setFormData({ name: '', type: 'AWS' });
    setIsModalOpen(true);
  };

  const openEditModal = (provider: any) => {
    setProviderToEdit(provider);
    setFormData({ name: provider.name, type: provider.type });
    setIsModalOpen(true);
  };

  const openDeleteModal = (provider: any) => {
    setProviderToDelete(provider);
    setDeleteError(null);
    setIsDeleteModalOpen(true);
  };

  return (
    <Layout>
      <div className="bg-gray-900 border border-gray-700 rounded-xl shadow-lg p-6 h-full flex flex-col relative">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold">Cloud Providers</h2>
          <button 
            onClick={openAddModal}
            className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors"
          >
            Add Provider
          </button>
        </div>
        
        {isLoading ? (
          <div className="text-center text-gray-400 p-8">Loading providers...</div>
        ) : providers?.length === 0 ? (
          <div className="text-center text-gray-400 p-8 border-2 border-dashed border-gray-700 rounded-lg">
            No providers configured. Click "Add Provider" to start.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {providers?.map(provider => (
              <div key={provider.id} className="bg-gray-800 border border-gray-700 rounded-lg p-6 flex flex-col relative group">
                <div className="flex justify-between items-start mb-4">
                  <h3 className="text-lg font-bold">{provider.name}</h3>
                  <div className="flex items-center space-x-2">
                    <span className={`px-2 py-1 text-xs font-medium rounded-full ${
                      provider.status === 'Connected' ? 'bg-green-500/20 text-green-400' : 'bg-yellow-500/20 text-yellow-400'
                    }`}>
                      {provider.status}
                    </span>
                  </div>
                </div>
                
                <div className="absolute top-4 right-4 flex space-x-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button onClick={() => openEditModal(provider)} className="text-gray-400 hover:text-blue-400" aria-label="Edit">
                    Edit
                  </button>
                  <button onClick={() => openDeleteModal(provider)} className="text-gray-400 hover:text-red-400" aria-label="Delete">
                    Delete
                  </button>
                </div>
                
                <p className="text-gray-400 text-sm mb-4">Type: {provider.type}</p>
                <div className="pt-4 border-t border-gray-700 text-sm text-gray-500 mb-4">
                  Last Sync: {provider.lastSync ? new Date(provider.lastSync).toLocaleString() : 'Never'}
                </div>
                <div className="mt-auto">
                  <button 
                    onClick={() => testConnectionMutation.mutate(provider.id)}
                    disabled={testConnectionMutation.isPending}
                    className="w-full bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                  >
                    {testConnectionMutation.isPending ? 'Testing...' : 'Test Connection'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {isModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md">
              <h3 className="text-xl font-bold mb-4">{providerToEdit ? 'Edit Cloud Provider' : 'Add Cloud Provider'}</h3>
              <form onSubmit={handleSaveSubmit} className="space-y-4">
                <div>
                  <label htmlFor="providerName" className="block text-sm font-medium mb-1">Provider Name</label>
                  <input 
                    id="providerName" 
                    type="text" 
                    required
                    value={formData.name}
                    onChange={e => setFormData({...formData, name: e.target.value})}
                    className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="e.g. Production AWS"
                  />
                </div>
                <div>
                  <label htmlFor="providerType" className="block text-sm font-medium mb-1">Provider Type</label>
                  <select 
                    id="providerType" 
                    value={formData.type}
                    onChange={e => setFormData({...formData, type: e.target.value})}
                    className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    disabled={!!providerToEdit}
                  >
                    <option value="AWS">AWS</option>
                    <option value="Azure">Azure</option>
                    <option value="GCP">GCP</option>
                  </select>
                </div>
                <div className="flex space-x-3 pt-4">
                  <button 
                    type="button"
                    onClick={() => setIsModalOpen(false)}
                    className="flex-1 bg-gray-700 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-md transition-colors"
                  >
                    Cancel
                  </button>
                  <button 
                    type="submit"
                    disabled={saveProviderMutation.isPending}
                    className="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                  >
                    {saveProviderMutation.isPending ? 'Saving...' : 'Save Provider'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {isDeleteModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md" role="dialog" aria-modal="true">
              <h3 className="text-xl font-bold text-red-500 mb-4">Delete Provider?</h3>
              {deleteError && (
                <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-md text-red-400 text-sm" role="alert">
                  {deleteError}
                </div>
              )}
              <p className="text-gray-300 mb-6">
                Are you sure you want to delete <strong>{providerToDelete?.name}</strong>?
              </p>
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
                  onClick={() => deleteProviderMutation.mutate(providerToDelete?.id)}
                  disabled={deleteProviderMutation.isPending}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                >
                  {deleteProviderMutation.isPending ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
};

export default Providers;
