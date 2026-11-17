import { useEffect, useState } from 'react'
import { getHealth } from '@/api/health'

export function useBackendHealth() {
  const [status, setStatus] = useState<'Connecting' | 'Connected' | 'Unavailable'>('Connecting')
  useEffect(() => {
    const controller = new AbortController()
    getHealth(controller.signal)
      .then(() => { if (!controller.signal.aborted) setStatus('Connected') })
      .catch(() => { if (!controller.signal.aborted) setStatus('Unavailable') })
    return () => controller.abort()
  }, [])
  return status
}
