import React, { useState } from 'react';
import Layout from '../components/Layout';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchApi } from '../services/api';
import NewScanModal from '../components/NewScanModal';

const Scans: React.FC = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [scanToEdit, setScanToEdit] = useState<any | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [scanToDelete, setScanToDelete] = useState<any | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [formData, setFormData] = useState({ name: '' });
  
  const [expandedScanId, setExpandedScanId] = useState<string | null>(null);
  const [selectedCert, setSelectedCert] = useState<any | null>(null);

  const { data: scans, isLoading } = useQuery({
    queryKey: ['scans'],
    queryFn: () => fetchApi<any[]>('/api/v1/scans'),
  });

  const { data: scanCertificates, isLoading: isCertsLoading } = useQuery({
    queryKey: ['scanCertificates', expandedScanId],
    queryFn: () => fetchApi<any>(`/api/v1/scans/${expandedScanId}/certificates`),
    enabled: !!expandedScanId,
  });

  const saveScanMutation = useMutation({
    mutationFn: (scan: any) => {
      if (isEditing && scanToEdit) {
        return fetchApi(`/api/v1/scans/${scanToEdit.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(scan)
        });
      } else {
        return fetchApi('/api/v1/scans', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(scan)
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scans'] });
      setIsModalOpen(false);
      setScanToEdit(null);
      setIsEditing(false);
      setFormData({ name: '' });
    }
  });

  const deleteScanMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/scans/${id}`, {
      method: 'DELETE'
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scans'] });
      setIsDeleteModalOpen(false);
      setScanToDelete(null);
      setDeleteError(null);
    },
    onError: () => {
      setDeleteError('Unable to delete scan. Internal Server Error.');
    }
  });

  const cancelScanMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/scans/${id}/cancel`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scans'] })
  });

  const retryScanMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/scans/${id}/retry`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scans'] })
  });

  const runAgainScanMutation = useMutation({
    mutationFn: (id: string) => fetchApi(`/api/v1/scans/${id}/run-again`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['scans'] })
  });

  const handleSaveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveScanMutation.mutate(formData);
  };

  const openAddModal = () => {
    setScanToEdit(null);
    setIsEditing(false);
    setIsModalOpen(true);
  };

  const openEditModal = (scan: any) => {
    setScanToEdit(scan);
    setIsEditing(true);
    setIsModalOpen(true);
  };

  const openRunAgainModal = (scan: any) => {
    // We now just trigger the backend run-again endpoint directly, 
    // but leaving this function stub if we ever want to revert to the copy-edit workflow.
    runAgainScanMutation.mutate(scan.id);
  };

  const openDeleteModal = (scan: any, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setScanToDelete(scan);
    setDeleteError(null);
    setIsDeleteModalOpen(true);
  };

  const toggleExpand = (scanId: string) => {
    if (expandedScanId === scanId) {
      setExpandedScanId(null);
    } else {
      setExpandedScanId(scanId);
    }
  };

  const handleExportCsv = (scan: any, certs: any[]) => {
    if (!certs || certs.length === 0) return;
    window.open(`/api/v1/scans/${scan.id}/certificates/export`, '_blank');
  };

  return (
    <Layout>
      <div className="bg-gray-900 border border-gray-700 rounded-xl shadow-lg p-6 h-full flex flex-col relative">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold">Scans</h2>
          <button 
            onClick={openAddModal}
            className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors"
          >
            New Scan
          </button>
        </div>
        
        {isLoading ? (
          <div className="text-center text-gray-400 p-8">Loading scans...</div>
        ) : scans?.length === 0 ? (
          <div className="text-center text-gray-400 p-8 border-2 border-dashed border-gray-700 rounded-lg">
            No scans found. Click "New Scan" to start.
          </div>
        ) : (
          <div className="overflow-x-auto border border-gray-700 rounded-lg">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-gray-800 border-b border-gray-700">
                  <th className="p-3 text-sm font-semibold text-gray-300">Scan Name</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Scope</th>
                  <th className="p-3 text-sm font-semibold text-gray-300 text-right">Accounts</th>
                  <th className="p-3 text-sm font-semibold text-gray-300 text-right">Certificates</th>
                  <th className="p-3 text-sm font-semibold text-gray-300">Status</th>
                  <th className="p-3 text-sm font-semibold text-gray-300 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {scans?.map(scan => (
                  <React.Fragment key={scan.id}>
                  <tr 
                    onClick={() => toggleExpand(scan.id)}
                    className="border-b border-gray-800 hover:bg-gray-800/50 transition-colors cursor-pointer"
                  >
                    <td className="p-3 text-sm font-medium">{scan.name}</td>
                    <td className="p-3 text-sm text-gray-400">
                      {scan.scopeType === 'PROVIDER' ? (scan.providers?.includes('all') ? 'All Providers' : scan.providers?.join(', ').toUpperCase())
                        : scan.scopeType === 'ENVIRONMENT' ? `${scan.providers?.join(', ').toUpperCase()} / ${scan.environments?.includes('all') ? 'All Envs' : scan.environments?.join(', ').toUpperCase()}`
                        : scan.scopeType === 'ACCOUNT' ? `${scan.providers?.join(', ').toUpperCase()} / ${scan.environments?.join(', ').toUpperCase()} / ${scan.accounts?.length} Accounts`
                        : scan.scopeType === 'CUSTOM' ? 'Custom Scope'
                        : (scan.provider || '-')}
                    </td>
                    <td className="p-3 text-sm text-gray-400 text-right">{scan.accountCount || scan.accounts?.length || 0}</td>
                    <td className="p-3 text-sm text-gray-400 text-right">{scan.certificatesFound || 0}</td>
                    <td className="p-3 text-sm">
                      <span className={`px-2 py-1 text-xs font-medium rounded-full ${
                        scan.status === 'Completed' ? 'bg-green-500/20 text-green-400' : 
                        scan.status === 'In Progress' ? 'bg-blue-500/20 text-blue-400' :
                        scan.status === 'Queued' ? 'bg-gray-500/20 text-gray-400' :
                        'bg-red-500/20 text-red-400'
                      }`}>
                        {scan.status}
                      </span>
                    </td>
                    <td className="p-3 text-sm text-right space-x-3" onClick={e => e.stopPropagation()}>
                      {scan.status === 'Queued' && (
                        <>
                          <button onClick={(e) => { e.stopPropagation(); openEditModal(scan); }} className="text-blue-400 hover:text-blue-300">Edit</button>
                          <button onClick={(e) => { e.stopPropagation(); cancelScanMutation.mutate(scan.id); }} className="text-gray-400 hover:text-white">Cancel Scan</button>
                        </>
                      )}
                      {scan.status === 'In Progress' && (
                        <button onClick={(e) => { e.stopPropagation(); cancelScanMutation.mutate(scan.id); }} className="text-gray-400 hover:text-white">Cancel Scan</button>
                      )}
                      {(scan.status === 'Completed' || scan.status === 'Failed' || scan.status === 'Partial Success') && (
                        <>
                          {scan.status === 'Failed' && (
                             <button onClick={(e) => { e.stopPropagation(); retryScanMutation.mutate(scan.id); }} className="text-yellow-400 hover:text-yellow-300">Retry</button>
                          )}
                          <button onClick={(e) => { e.stopPropagation(); runAgainScanMutation.mutate(scan.id); }} className="text-blue-400 hover:text-blue-300">Run Again</button>
                          <button onClick={(e) => { e.stopPropagation(); openDeleteModal(scan, e); }} className="text-red-400 hover:text-red-300">Delete</button>
                        </>
                      )}
                    </td>
                  </tr>
                  {expandedScanId === scan.id && (
                    <tr className="bg-gray-900 border-b border-gray-700">
                      <td colSpan={7} className="p-0">
                        <div className="p-6 bg-gray-800/30 border-l-4 border-blue-500">
                          
                          {scan.status === 'In Progress' && (
                            <div className="mb-6 p-4 bg-gray-800 rounded-lg border border-gray-700">
                              <h4 className="text-blue-400 font-bold mb-2">IN PROGRESS</h4>
                              <p className="text-sm text-gray-300 mb-1">Progress: {scan.progress || 0}%</p>
                              <p className="text-sm text-gray-300 mb-1">Accounts: {scan.completedAccounts || 0} / {scan.accountCount || 0} complete</p>
                              <p className="text-sm text-gray-300">Certificates discovered: {scan.certificatesFound || 0}</p>
                            </div>
                          )}

                          {scan.status === 'Failed' && (
                            <div className="mb-6 p-4 bg-red-900/20 rounded-lg border border-red-700/50">
                              <h4 className="text-red-500 font-bold mb-2">FAILED</h4>
                              <p className="text-sm text-gray-300">Reason: {scan.failureReason || 'Unknown error occurred.'}</p>
                            </div>
                          )}

                          {scan.status === 'Partial Success' && (
                            <div className="mb-6 p-4 bg-yellow-900/20 rounded-lg border border-yellow-700/50">
                              <h4 className="text-yellow-500 font-bold mb-2">PARTIAL SUCCESS</h4>
                              <p className="text-sm text-gray-300 mb-1">Accounts scanned: {scan.successfulAccounts || 0} successful, {scan.failedAccounts || 0} failed</p>
                              <p className="text-sm text-gray-300">Certificates discovered: {scan.certificatesFound || 0}</p>
                            </div>
                          )}

                          {(scan.status === 'Completed' || scan.status === 'Partial Success') && (
                            <div>
                              <div className="flex justify-between items-center mb-4">
                                <h3 className="text-lg font-bold">Certificates Discovered</h3>
                                <button 
                                  onClick={() => handleExportCsv(scan, scanCertificates?.items || [])}
                                  className="bg-gray-700 hover:bg-gray-600 text-white font-medium py-1 px-3 rounded text-sm transition-colors"
                                >
                                  Export CSV
                                </button>
                              </div>
                              
                              {isCertsLoading ? (
                                <div className="text-gray-400 p-4">Loading certificates...</div>
                              ) : scanCertificates?.items?.length > 0 ? (
                                <div className="overflow-x-auto border border-gray-700 rounded bg-gray-900">
                                  <table className="w-full text-left border-collapse">
                                    <thead>
                                      <tr className="bg-gray-800 border-b border-gray-700">
                                        <th className="p-2 text-xs font-semibold text-gray-300">Certificate</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Domain</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Account</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Environment</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Region</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Service</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Status</th>
                                        <th className="p-2 text-xs font-semibold text-gray-300">Expiry</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {scanCertificates.items.map((cert: any) => (
                                        <tr 
                                          key={cert.id} 
                                          className="border-b border-gray-800 hover:bg-gray-800 transition-colors cursor-pointer"
                                          onClick={() => setSelectedCert(cert)}
                                        >
                                          <td className="p-2 text-xs text-gray-300">{cert.certificateId}</td>
                                          <td className="p-2 text-xs font-medium text-white">{cert.domain}</td>
                                          <td className="p-2 text-xs text-gray-400">{cert.accountName}</td>
                                          <td className="p-2 text-xs text-gray-400">{cert.environment}</td>
                                          <td className="p-2 text-xs text-gray-400">{cert.region}</td>
                                          <td className="p-2 text-xs text-gray-400">{cert.service}</td>
                                          <td className="p-2 text-xs">
                                            <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${
                                              cert.status === 'ACTIVE' ? 'bg-green-500/20 text-green-400' :
                                              cert.status === 'EXPIRING SOON' ? 'bg-yellow-500/20 text-yellow-400' :
                                              'bg-red-500/20 text-red-400'
                                            }`}>
                                              {cert.status}
                                            </span>
                                          </td>
                                          <td className="p-2 text-xs text-gray-400">{cert.expiryDate}</td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              ) : (
                                <div className="text-gray-400 p-4 border border-gray-700 rounded bg-gray-900">
                                  No certificates found.
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {isModalOpen && (
          <NewScanModal 
            initialScan={scanToEdit}
            onClose={() => {
              setIsModalOpen(false);
              setScanToEdit(null);
              setIsEditing(false);
            }}
            onSave={(scanData) => saveScanMutation.mutate(scanData)}
            isSaving={saveScanMutation.isPending}
          />
        )}

        {isDeleteModalOpen && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-md" role="dialog" aria-modal="true">
              <h3 className="text-xl font-bold text-red-500 mb-4">Delete Scan?</h3>
              {deleteError && (
                <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-md text-red-400 text-sm" role="alert">
                  {deleteError}
                </div>
              )}
              
              <div className="mb-6">
                <p className="text-gray-300 mb-2">
                  Are you sure you want to delete this scan: <strong>{scanToDelete?.name}</strong>?
                </p>
                <p className="text-gray-400 text-sm mt-2">
                  This action cannot be undone. Any historical certificates found by this scan will remain in the inventory.
                </p>
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
                  onClick={() => deleteScanMutation.mutate(scanToDelete?.id)}
                  disabled={deleteScanMutation.isPending}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
                >
                  {deleteScanMutation.isPending ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}

        {selectedCert && (
          <div className="absolute inset-0 bg-black/50 flex items-center justify-center p-4 z-[60]" onClick={() => setSelectedCert(null)}>
            <div className="bg-gray-800 border border-gray-700 rounded-xl shadow-2xl p-6 w-full max-w-lg" role="dialog" aria-modal="true" onClick={e => e.stopPropagation()}>
              <div className="flex justify-between items-start mb-4">
                <h3 className="text-xl font-bold">Certificate Details</h3>
                <button onClick={() => setSelectedCert(null)} className="text-gray-400 hover:text-white">&times;</button>
              </div>
              
              <div className="space-y-4 max-h-[70vh] overflow-y-auto pr-2">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Certificate ID</label>
                    <div className="text-sm text-gray-200">{selectedCert.certificateId}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Status</label>
                    <div className="text-sm font-medium text-white">{selectedCert.status}</div>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">Domain</label>
                  <div className="text-sm text-blue-400">{selectedCert.domain}</div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Provider</label>
                    <div className="text-sm text-gray-200">{selectedCert.provider}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Environment</label>
                    <div className="text-sm text-gray-200">{selectedCert.environment}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Account Name</label>
                    <div className="text-sm text-gray-200">{selectedCert.accountName}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Account ID</label>
                    <div className="text-sm text-gray-200">{selectedCert.accountId}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Region</label>
                    <div className="text-sm text-gray-200">{selectedCert.region}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Service / Resource</label>
                    <div className="text-sm text-gray-200">{selectedCert.service} / {selectedCert.resource}</div>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Issued Date</label>
                    <div className="text-sm text-gray-200">{selectedCert.issuedDate}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Expiry Date</label>
                    <div className="text-sm text-gray-200">{selectedCert.expiryDate}</div>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">Issuer</label>
                  <div className="text-sm text-gray-200">{selectedCert.issuer}</div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Algorithm</label>
                    <div className="text-sm text-gray-200">{selectedCert.algorithm}</div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">Auto Renewal</label>
                    <div className="text-sm text-gray-200">{selectedCert.autoRenewal ? 'Enabled' : 'Disabled'}</div>
                  </div>
                </div>
                
                {selectedCert.sans && selectedCert.sans.length > 0 && (
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 mb-1">SANs</label>
                    <div className="text-sm text-gray-200">
                      {selectedCert.sans.map((san: string) => <div key={san}>{san}</div>)}
                    </div>
                  </div>
                )}
                
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">Last Scanned</label>
                  <div className="text-sm text-gray-200">{selectedCert.lastScanned}</div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
};

export default Scans;
