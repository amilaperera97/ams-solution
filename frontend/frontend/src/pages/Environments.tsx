import React, { useState } from 'react';
import Layout from '../components/Layout';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchApi } from '../services/api';

const Environments: React.FC = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [envToEdit, setEnvToEdit] = useState<any | null>(null);
  const [envToDelete, setEnvToDelete] = useState<any | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [newEnvName, setNewEnvName] = useState('');

  const [selectedProviderId, setSelectedProviderId] = useState<string>('');
  const [formProviderId, setFormProviderId] = useState<string>('');

  const { data: org } = useQuery({
    queryKey: ['organization'],
    queryFn: () => fetchApi<any>('/api/v1/organisations/current'),
  });

  const { data: providers } = useQuery({
    queryKey: ['providers', org?.id],
    queryFn: () => fetchApi<any[]>(`/api/v1/organisations/${org?.id}/providers`),
    enabled: !!org?.id
  });

  // Auto-select first provider if none selected
  React.useEffect(() => {
    if (providers && providers.length > 0 && !selectedProviderId) {
      setSelectedProviderId(providers[0].id);
    }
  }, [providers, selectedProviderId]);

  const { data: environments, isLoading } = useQuery({
    queryKey: ['environments', selectedProviderId],
    queryFn: () => fetchApi<any[]>(`/api/v1/providers/${selectedProviderId}/environments`),
    enabled: !!selectedProviderId,
  });

  const saveEnvMutation = useMutation({
    mutationFn: (env: any) => {
      if (envToEdit) {
        return fetchApi(`/api/v1/environments/${envToEdit.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(env)
        });
      } else {
        return fetchApi(`/api/v1/providers/${env.providerId}/environments`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: env.name, description: env.description || '' })
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments'] });
      setIsModalOpen(false);
      setEnvToEdit(null);
      setNewEnvName('');
    }
  });

  const deleteEnvMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/environments/${id}`, {
      method: 'DELETE'
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments'] });
      setIsDeleteModalOpen(false);
      setEnvToDelete(null);
      setDeleteError(null);
    },
    onError: () => {
      setDeleteError('Unable to delete environment. Internal Server Error.');
    }
  });

  const handleSaveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveEnvMutation.mutate({ name: newEnvName, providerId: formProviderId });
  };

  const openAddModal = () => {
    setEnvToEdit(null);
    setNewEnvName('');
    setFormProviderId(selectedProviderId || (providers?.[0]?.id || ''));
    setIsModalOpen(true);
  };

  const openEditModal = (env: any) => {
    setEnvToEdit(env);
    setNewEnvName(env.name);
    setFormProviderId(selectedProviderId);
    setIsModalOpen(true);
  };

  const openDeleteModal = (env: any) => {
    setEnvToDelete(env);
    setDeleteError(null);
    setIsDeleteModalOpen(true);
  };

  const hasDependentAccounts = (env: any) => env?.accountsCount > 0;

  return (
    <Layout>
      <div className="bg-gray-900 border border-gray-700 rounded-xl shadow-lg p-6 h-full flex flex-col relative">
        <div className="flex justify-between items-center mb-6">
          <div className="flex items-center space-x-4">
            <h2 className="text-2xl font-bold">Environments</h2>
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
          </div>
          <button 
            onClick={openAddModal}
            className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors"
          >
            Add Environment
          </button>
        </div>
        
        {isLoading ? (
          <div className="text-center text-gray-400 p-8">Loading environments...</div>
        ) : environments?.length === 0 ? (
          <div className="text-center text-gray-400 p-8 border-2 border-dashed border-gray-700 rounded-lg">
            No environments configured. Click "Add Environment" to start.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {environments?.map(env => (
              <div key={env.id} className="bg-gray-800 border border-gray-700 rounded-lg p-6 relative group">
                <div className="absolute top-4 right-4 flex space-x-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button onClick={() => openEditModal(env)} className="text-gray-400 hover:text-blue-400" aria-label="Edit">
                    Edit
                  </button>
                  <button onClick={() => openDeleteModal(env)} className="text-gray-400 hover:text-red-400" aria-label="Delete">
                    Delete
                  </button>
                </div>
                <h3 className="text-xl font-bold mb-2">{env.name}</h3>
                <p className="text-gray-400 text-sm mb-1">Accounts: {env.accountsCount || 0}</p>
                <p className="text-gray-400 text-sm">Certificates: {env.certificatesCount || 0}</p>
              </div>
            ))}
          </div>
        )}

        {isModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md">
              <h3 className="text-xl font-bold mb-4">{envToEdit ? 'Edit Environment' : 'Add Environment'}</h3>
              <form onSubmit={handleSaveSubmit} className="space-y-4">
                <div>
                  <label htmlFor="formProvider" className="block text-sm font-medium mb-1">Provider</label>
                  <select
                    id="formProvider"
                    value={formProviderId}
                    onChange={e => setFormProviderId(e.target.value)}
                    disabled={!!envToEdit}
                    className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                  >
                    {providers?.map(p => (
                      <option key={p.id} value={p.id}>{p.name} ({p.type})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="envName" className="block text-sm font-medium mb-1">Environment Name</label>
                  <input 
                    id="envName" 
                    type="text" 
                    required
                    value={newEnvName}
                    onChange={e => setNewEnvName(e.target.value)}
                    className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500 text-white"
                    placeholder="e.g. PROD"
                  />
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
                    disabled={saveEnvMutation.isPending}
                    className="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                  >
                    {saveEnvMutation.isPending ? 'Saving...' : 'Save Environment'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {isDeleteModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md" role="dialog" aria-modal="true">
              <h3 className="text-xl font-bold text-red-500 mb-4">Delete Environment?</h3>
              {deleteError && (
                <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-md text-red-400 text-sm" role="alert">
                  {deleteError}
                </div>
              )}
              
              {hasDependentAccounts(envToDelete) ? (
                <div className="mb-6 p-4 bg-yellow-500/20 border border-yellow-500 rounded-md">
                  <p className="text-yellow-400 font-medium">Warning: Dependent Accounts</p>
                  <p className="text-gray-300 text-sm mt-1">
                    {envToDelete?.accountsCount} accounts are associated with this environment. You must remove or reassign these accounts before deleting this environment.
                  </p>
                </div>
              ) : (
                <p className="text-gray-300 mb-6">
                  Are you sure you want to delete this environment: <strong>{envToDelete?.name}</strong>?
                </p>
              )}
              
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
                  onClick={() => deleteEnvMutation.mutate(envToDelete?.id)}
                  disabled={deleteEnvMutation.isPending || hasDependentAccounts(envToDelete)}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {deleteEnvMutation.isPending ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
};

export default Environments;
