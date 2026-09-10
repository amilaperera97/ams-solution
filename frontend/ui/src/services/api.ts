export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

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
    throw new Error(`API error: ${response.status} ${response.statusText}`);
  }

  // Some endpoints might return empty body (e.g. DELETE)
  const text = await response.text();
  return text ? JSON.parse(text) : (undefined as unknown as T);
}
