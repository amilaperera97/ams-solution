import React from 'react';
import Layout from '../components/Layout';
import { useDashboardStats } from '../hooks/useApi';
import { Cloud, Server, Users, Shield, AlertTriangle } from 'lucide-react';
import { Link } from 'react-router-dom';

const Dashboard: React.FC = () => {
  const { data: stats, isLoading } = useDashboardStats();

  if (isLoading) return <Layout><div className="flex items-center justify-center h-full">Loading dashboard...</div></Layout>;

  return (
    <Layout>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <div className="bg-gray-800 rounded-xl p-6 border border-gray-700 shadow-md">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-gray-400 font-medium">Cloud Providers</h3>
            <Cloud className="text-blue-500" />
          </div>
          <p className="text-3xl font-bold">{stats?.totalProviders || 0}</p>
        </div>
        
        <div className="bg-gray-800 rounded-xl p-6 border border-gray-700 shadow-md">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-gray-400 font-medium">Environments</h3>
            <Server className="text-purple-500" />
          </div>
          <p className="text-3xl font-bold">{stats?.totalEnvironments || 0}</p>
        </div>
        
        <div className="bg-gray-800 rounded-xl p-6 border border-gray-700 shadow-md">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-gray-400 font-medium">Accounts</h3>
            <Users className="text-green-500" />
          </div>
          <p className="text-3xl font-bold">{stats?.totalAccounts || 0}</p>
        </div>
        
        <div className="bg-gray-800 rounded-xl p-6 border border-gray-700 shadow-md">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-gray-400 font-medium">Total Certificates</h3>
            <Shield className="text-indigo-500" />
          </div>
          <p className="text-3xl font-bold">{stats?.totalCertificates || 0}</p>
        </div>
      </div>

      <h2 className="text-xl font-bold mb-4">Certificate Health</h2>
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        <Link to="/certificates?status=Expired" className="bg-gray-800 rounded-xl p-4 border-b-4 border-red-500 hover:bg-gray-700 transition">
          <p className="text-gray-400 text-sm">Expired</p>
          <p className="text-2xl font-bold text-red-500">{stats?.certificateHealth?.expired || 0}</p>
        </Link>
        <Link to="/certificates?status=Critical" className="bg-gray-800 rounded-xl p-4 border-b-4 border-orange-500 hover:bg-gray-700 transition">
          <p className="text-gray-400 text-sm">Critical</p>
          <p className="text-2xl font-bold text-orange-500">{stats?.certificateHealth?.critical || 0}</p>
        </Link>
        <Link to="/certificates?status=Expiring%20Soon" className="bg-gray-800 rounded-xl p-4 border-b-4 border-yellow-500 hover:bg-gray-700 transition">
          <p className="text-gray-400 text-sm">Expiring {'<'} 30 days</p>
          <p className="text-2xl font-bold text-yellow-500">{stats?.certificateHealth?.expiringSoon || 0}</p>
        </Link>
        <Link to="/certificates?status=Healthy" className="bg-gray-800 rounded-xl p-4 border-b-4 border-green-500 hover:bg-gray-700 transition">
          <p className="text-gray-400 text-sm">Healthy</p>
          <p className="text-2xl font-bold text-green-500">{stats?.certificateHealth?.healthy || 0}</p>
        </Link>
      </div>
    </Layout>
  );
};

export default Dashboard;
