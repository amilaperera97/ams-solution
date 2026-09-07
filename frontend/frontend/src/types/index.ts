export interface Organization {
  id: string;
  name: string;
  setupComplete: boolean;
}

export interface Provider {
  id: string;
  type: 'AWS' | 'Azure' | 'GCP';
  name: string;
  status: 'CONNECTED' | 'WARNING' | 'FAILED';
  accountsCount: number;
  environmentsCount: number;
  certificatesCount: number;
}

export interface Environment {
  id: string;
  name: string;
  description: string;
  accountsCount: number;
  certificatesCount: number;
}

export interface Account {
  id: string;
  providerId: string;
  provider: string;
  environment: string;
  name: string;
  accountId: string;
  status: 'CONNECTED' | 'WARNING' | 'FAILED';
  certificateCount: number;
}

export interface Certificate {
  id: string;
  name: string;
  domain: string;
  provider: string;
  environment: string;
  account: string;
  region: string;
  service: string;
  status: 'Healthy' | 'Expiring Soon' | 'Critical' | 'Expired' | 'Unknown';
  issuedAt: string;
  expiresAt: string;
  issuer: string;
  certificateType: string;
  lastDiscoveredAt: string;
  dependencies?: any[];
  providerMetadata?: Record<string, any>;
}

export interface DashboardStats {
  providers: number;
  environments: number;
  accounts: number;
  certificates: number;
  health: {
    expired: number;
    critical: number;
    expiringSoon: number;
    healthy: number;
  };
}
