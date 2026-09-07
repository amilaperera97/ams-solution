import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchApi } from '../services/api';
import type { Organization, Provider, Environment, Account, Certificate, DashboardStats } from '../types';

export const useOrganization = () => {
  return useQuery({
    queryKey: ['organization'],
    queryFn: () => fetchApi<Organization>('/api/v1/organisations/current'),
  });
};

export const useProviders = (orgId?: string) => {
  return useQuery({
    queryKey: ['providers', orgId],
    queryFn: () => fetchApi<Provider[]>(`/api/v1/organisations/${orgId}/providers`),
    enabled: !!orgId
  });
};

export const useEnvironments = (providerId?: string) => {
  return useQuery({
    queryKey: ['environments', providerId],
    queryFn: () => fetchApi<Environment[]>(`/api/v1/providers/${providerId}/environments`),
    enabled: !!providerId
  });
};

export const useAccounts = (environmentId?: string, filters?: Record<string, string>) => {
  return useQuery({
    queryKey: ['accounts', environmentId, filters],
    queryFn: () => {
      let url = `/api/v1/environments/${environmentId}/accounts`;
      if (filters && Object.keys(filters).length > 0) {
        const params = new URLSearchParams(filters);
        url += `?${params.toString()}`;
      }
      return fetchApi<Account[]>(url);
    },
    enabled: !!environmentId
  });
};

export const useCertificates = (scanId?: string, filters?: Record<string, string>) => {
  return useQuery({
    queryKey: ['certificates', scanId, filters],
    queryFn: () => {
      let url = `/api/v1/scans/${scanId}/certificates`;
      if (filters && Object.keys(filters).length > 0) {
        const params = new URLSearchParams(filters);
        url += `?${params.toString()}`;
      }
      return fetchApi<Certificate[]>(url);
    },
    enabled: !!scanId
  });
};

export const useDashboardStats = () => {
  return useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => fetchApi<DashboardStats>('/api/v1/dashboard/stats'),
  });
};
