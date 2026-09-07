import React, { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import Layout from '../components/Layout';
import { useCertificates } from '../hooks/useApi';
import { Filter, Search, Download } from 'lucide-react';

const Certificates: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  
  // Extract all active filters from the URL
  const filters = useMemo(() => {
    const f: Record<string, string> = {};
    for (const [key, value] of searchParams.entries()) {
      f[key] = value;
    }
    return f;
  }, [searchParams]);

  const { data: allCerts, isLoading } = useCertificates();

  // Apply filters client-side for now since wiremock doesn't natively do advanced filtering 
  // without extensive mock definitions, but the API hook passes them to allow backend filtering later.
  const certificates = useMemo(() => {
    if (!allCerts) return [];
    return allCerts.filter(cert => {
      let matches = true;
      if (filters.provider && cert.provider !== filters.provider) matches = false;
      if (filters.environment && cert.environment !== filters.environment) matches = false;
      if (filters.status && cert.status !== filters.status) matches = false;
      if (filters.account && cert.account !== filters.account) matches = false;
      if (filters.service && cert.service !== filters.service) matches = false;
      if (filters.search) {
        const query = filters.search.toLowerCase();
        if (!cert.domain.toLowerCase().includes(query) && !cert.name.toLowerCase().includes(query)) {
          matches = false;
        }
      }
      return matches;
    });
  }, [allCerts, filters]);

  const updateFilter = (key: string, value: string) => {
    const newParams = new URLSearchParams(searchParams);
    if (value) {
      newParams.set(key, value);
    } else {
      newParams.delete(key);
    }
    setSearchParams(newParams);
  };

  const statusColors: Record<string, string> = {
    'Healthy': 'bg-green-500/20 text-green-400',
    'Expiring Soon': 'bg-yellow-500/20 text-yellow-400',
    'Critical': 'bg-orange-500/20 text-orange-400',
    'Expired': 'bg-red-500/20 text-red-400',
  };

  return (
    <Layout>
      <div className="flex flex-col h-full bg-gray-900 border border-gray-700 rounded-xl overflow-hidden shadow-lg">
        {/* Toolbar */}
        <div className="p-4 bg-gray-800 border-b border-gray-700 flex flex-wrap gap-4 items-center justify-between">
          <div className="flex flex-wrap gap-4 items-center">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={18} />
              <input 
                type="text" 
                placeholder="Search domain..." 
                className="bg-gray-900 border border-gray-700 rounded-md pl-10 pr-4 py-2 text-sm focus:outline-none focus:border-blue-500"
                value={filters.search || ''}
                onChange={e => updateFilter('search', e.target.value)}
              />
            </div>
            
            <div className="flex items-center space-x-2">
              <Filter size={18} className="text-gray-400" />
              <select 
                className="bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
                value={filters.provider || ''}
                onChange={e => updateFilter('provider', e.target.value)}
              >
                <option value="">All Providers</option>
                <option value="AWS">AWS</option>
                <option value="Azure">Azure</option>
                <option value="GCP">GCP</option>
              </select>

              <select 
                className="bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
                value={filters.status || ''}
                onChange={e => updateFilter('status', e.target.value)}
              >
                <option value="">All Statuses</option>
                <option value="Healthy">Healthy</option>
                <option value="Expiring Soon">Expiring Soon</option>
                <option value="Critical">Critical</option>
                <option value="Expired">Expired</option>
              </select>
            </div>
          </div>
          
          <button 
            onClick={() => window.open('/api/v1/scans/scan-mock/certificates/export', '_blank')}
            className="flex items-center space-x-2 bg-gray-700 hover:bg-gray-600 px-4 py-2 rounded-md text-sm font-medium transition-colors"
          >
            <Download size={18} />
            <span>Export CSV</span>
          </button>
        </div>

        {/* Table */}
        <div className="flex-1 overflow-auto">
          {isLoading ? (
            <div className="p-8 text-center text-gray-400">Loading certificates...</div>
          ) : certificates.length === 0 ? (
            <div className="p-8 text-center text-gray-400">No certificates found.</div>
          ) : (
            <table className="w-full text-left text-sm whitespace-nowrap">
              <thead className="bg-gray-800/50 sticky top-0">
                <tr>
                  <th className="px-6 py-3 font-medium text-gray-400">Domain</th>
                  <th className="px-6 py-3 font-medium text-gray-400">Status</th>
                  <th className="px-6 py-3 font-medium text-gray-400">Provider</th>
                  <th className="px-6 py-3 font-medium text-gray-400">Environment</th>
                  <th className="px-6 py-3 font-medium text-gray-400">Service</th>
                  <th className="px-6 py-3 font-medium text-gray-400">Expires</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800">
                {certificates.map(cert => (
                  <tr key={cert.id} className="hover:bg-gray-800/50 transition-colors cursor-pointer">
                    <td className="px-6 py-4 font-medium">{cert.domain}</td>
                    <td className="px-6 py-4">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[cert.status] || 'bg-gray-500/20 text-gray-400'}`}>
                        {cert.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-gray-300">{cert.provider}</td>
                    <td className="px-6 py-4 text-gray-300">{cert.environment}</td>
                    <td className="px-6 py-4 text-gray-300">{cert.service}</td>
                    <td className="px-6 py-4 text-gray-300">
                      {new Date(cert.expiresAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </Layout>
  );
};

export default Certificates;
