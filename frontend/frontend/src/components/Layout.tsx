import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, Cloud, Server, Users, Shield, Settings, Activity } from 'lucide-react';
import { useOrganization } from '../hooks/useApi';

const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { data: org } = useOrganization();
  const location = useLocation();

  const navItems = [
    { name: 'Dashboard', path: '/dashboard', icon: <LayoutDashboard size={20} /> },
    { name: 'Cloud Providers', path: '/providers', icon: <Cloud size={20} /> },
    { name: 'Environments', path: '/environments', icon: <Server size={20} /> },
    { name: 'Accounts', path: '/accounts', icon: <Users size={20} /> },
    { name: 'Certificates', path: '/certificates', icon: <Shield size={20} /> },
    { name: 'Scans', path: '/scans', icon: <Activity size={20} /> },
    { name: 'Configuration', path: '/configuration', icon: <Settings size={20} /> },
  ];

  return (
    <div className="flex h-screen bg-gray-900 text-white font-sans">
      {/* Sidebar */}
      <aside className="w-64 bg-gray-800 border-r border-gray-700 flex flex-col">
        <div className="p-6">
          <h2 className="text-xl font-bold flex items-center text-blue-400">
            <Shield className="mr-2" /> CertDiscovery
          </h2>
        </div>
        
        <nav className="flex-1 px-4 space-y-1">
          {navItems.map((item) => {
            const isActive = location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.name}
                to={item.path}
                className={`flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-colors ${
                  isActive ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-gray-700'
                }`}
              >
                {item.icon}
                <span className="ml-3">{item.name}</span>
              </Link>
            );
          })}
        </nav>

        <div className="p-4 border-t border-gray-700">
          <p className="text-sm text-gray-400">Organisation</p>
          <p className="font-medium truncate">{org?.name || 'Loading...'}</p>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden">
        <header className="h-16 bg-gray-800 border-b border-gray-700 flex items-center justify-between px-8">
          <h1 className="text-xl font-semibold capitalize">
            {location.pathname.split('/')[1] || 'Dashboard'}
          </h1>
          <div className="flex items-center space-x-4">
            <Link to="/scans" className="bg-blue-600 hover:bg-blue-700 px-4 py-2 rounded-md text-sm font-medium transition-colors">
              Scan Now
            </Link>
          </div>
        </header>
        
        <div className="flex-1 overflow-auto p-8">
          {children}
        </div>
      </main>
    </div>
  );
};

export default Layout;
