import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useOrganization } from '../hooks/useApi';

const SetupPage: React.FC = () => {
  const { data: org, isLoading } = useOrganization();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (org?.setupComplete) {
      navigate('/dashboard');
    }
  }, [org, navigate]);

  if (isLoading) return <div className="p-8 text-center">Loading...</div>;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    
    try {
      const response = await fetch('/api/v1/organisations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: 'Created via Setup' })
      });
      
      if (response.ok) {
        queryClient.invalidateQueries({ queryKey: ['organization'] });
        navigate('/dashboard');
      } else {
        setError(`Failed to save: ${response.status} ${response.statusText}`);
      }
    } catch (err: any) {
      setError(`Network error: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-900 text-white p-4">
      <div className="w-full max-w-md bg-gray-800 rounded-xl p-8 shadow-xl border border-gray-700">
        <h1 className="text-2xl font-bold mb-2">Welcome to Certificate Discovery</h1>
        <p className="text-gray-400 mb-6">Setup Progress: 0%</p>
        
        {error && (
          <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-md text-red-400 text-sm">
            {error}
          </div>
        )}
        
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="orgName" className="block text-sm font-medium mb-1">Organisation Name</label>
            <input 
              id="orgName" 
              type="text" 
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full bg-gray-900 border border-gray-700 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. ABC Corporation"
              disabled={isSubmitting}
            />
          </div>
          <button 
            type="submit"
            disabled={isSubmitting}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors disabled:opacity-50"
          >
            {isSubmitting ? 'Saving...' : 'Save & Continue'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default SetupPage;
