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

export type AccountAuthType = 'TOKEN' | 'IAM_ROLE' | 'ACCESS_KEY';

/**
 * Mirrors the backend's AccountResponse. Secrets are deliberately absent: the API
 * never returns a token, role ARN or secret access key, only whether one is stored,
 * and the access key id arrives masked to its last four characters.
 */
export interface Account {
  id: string;
  providerId: string;
  provider: string;
  environmentId?: string;
  environment: string;
  name: string;
  accountId: string;
  authType: AccountAuthType;
  region?: string;
  accessKeyId?: string;
  credentialsConfigured?: boolean;
  roleArnConfigured?: boolean;
  externalIdConfigured?: boolean;
  status: 'CONNECTED' | 'WARNING' | 'FAILED';
  certificateCount: number;
}

export type ProviderMode = 'MOCK' | 'REAL';

export interface CloudProviderConfig {
  /** REAL means calls leave for the actual cloud API; MOCK means they are simulated. */
  mode: ProviderMode;
  defaultRegion?: string;
  /** True when AWS_ENDPOINT_OVERRIDE points the SDK at LocalStack instead of AWS. */
  endpointOverridden?: boolean;
}

/** Keyed by provider type, e.g. { AWS: { mode: 'REAL' } }. */
export type CloudProviderConfigMap = Record<string, CloudProviderConfig>;

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
