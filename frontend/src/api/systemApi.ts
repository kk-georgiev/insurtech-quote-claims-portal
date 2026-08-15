export type SystemInfo = {
  status: string;
  project: string;
  stack: string[];
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export async function getSystemInfo(signal?: AbortSignal): Promise<SystemInfo> {
  const response = await fetch(`${apiBaseUrl}/api/v1/system/info`, { signal });

  if (!response.ok) {
    throw new Error(`Backend responded with HTTP ${response.status}`);
  }

  return response.json() as Promise<SystemInfo>;
}
