import { requestJson } from './client'

export interface HealthResponse {
  status: 'UP'
  application: 'Hustivoo'
  developer: 'Margraf Digital'
}

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const data = await requestJson('/api/v1/health', { signal })
  if (
    typeof data !== 'object' ||
    data === null ||
    !('status' in data) || data.status !== 'UP' ||
    !('application' in data) || data.application !== 'Hustivoo' ||
    !('developer' in data) || data.developer !== 'Margraf Digital'
  ) {
    throw new Error('Unexpected health response')
  }
  return {
    status: data.status,
    application: data.application,
    developer: data.developer,
  }
}
