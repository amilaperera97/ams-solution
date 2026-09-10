export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

/** Carries the backend's own message so a caller can show it verbatim. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function fetchApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    // The backend answers 400 with the reason as a plain-text body
    // (GlobalExceptionHandler), so keep it - it is the only thing that tells an
    // operator which field the API rejected.
    const detail = await response.text().catch(() => '');
    const error = new ApiError(
      detail.trim() || `API error: ${response.status} ${response.statusText}`,
      response.status,
    );
    throw error;
  }

  // Some endpoints might return empty body (e.g. DELETE)
  const text = await response.text();
  return text ? JSON.parse(text) : (undefined as unknown as T);
}
