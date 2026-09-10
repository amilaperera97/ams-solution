import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SetupPage from './pages/SetupPage';
import Dashboard from './pages/Dashboard';
import Certificates from './pages/Certificates';

import Providers from './pages/Providers';
import Environments from './pages/Environments';
import Accounts from './pages/Accounts';
import Scans from './pages/Scans';
import Configuration from './pages/Configuration';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<SetupPage />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/providers" element={<Providers />} />
          <Route path="/environments" element={<Environments />} />
          <Route path="/accounts" element={<Accounts />} />
          <Route path="/certificates" element={<Certificates />} />
          <Route path="/scans" element={<Scans />} />
          <Route path="/configuration" element={<Configuration />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
