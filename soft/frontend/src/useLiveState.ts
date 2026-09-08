import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from './api'
import type { DemoState, Session } from './types'

export function useLiveState(session: Session | null, onExpired: () => void) {
  const [loaded, setLoaded] = useState<{ token: string; state: DemoState } | null>(null)
  const [connected, setConnected] = useState(false)
  const token = session?.token
  const currentToken = useRef(token)
  currentToken.current = token
  const reload = useRef<() => Promise<void>>(async () => {})
  const refresh = useCallback(() => reload.current(), [])

  useEffect(() => {
    setLoaded(null)
    setConnected(false)
    if (!token) { reload.current = async () => {}; return }
    const lifecycle = new AbortController()
    let dirty = false
    let running: Promise<void> | null = null
    function refreshState(): Promise<void> {
      dirty = true
      if (running) return running
      running = (async () => {
        try {
          do {
            dirty = false
            const next = await api<DemoState>('/api/demo/state', token, undefined, lifecycle.signal)
            if (!dirty && !lifecycle.signal.aborted && currentToken.current === token) setLoaded({ token: token!, state: next })
          } while (dirty && !lifecycle.signal.aborted)
        } catch (error) {
          if (!lifecycle.signal.aborted && currentToken.current === token && error instanceof ApiError && ['AUTH_REQUIRED', 'SESSION_INVALID'].includes(error.code)) onExpired()
        } finally { running = null }
      })()
      return running
    }
    reload.current = refreshState
    void refreshState()

    async function stream() {
      let delay = 1000
      while (!lifecycle.signal.aborted) {
        const connection = new AbortController()
        const stop = () => connection.abort()
        lifecycle.signal.addEventListener('abort', stop, { once: true })
        let watchdog = window.setTimeout(stop, 60000)
        try {
          const response = await fetch('/api/events', {
            headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
            signal: connection.signal
          })
          if (response.status === 401) {
            if (!lifecycle.signal.aborted && currentToken.current === token) onExpired()
            return
          }
          if (!response.ok || !response.body) throw new Error('SSE unavailable')
          const reader = response.body.getReader()
          const decoder = new TextDecoder()
          let buffer = ''
          while (!connection.signal.aborted) {
            const { value, done } = await reader.read()
            if (done) break
            window.clearTimeout(watchdog)
            watchdog = window.setTimeout(stop, 60000)
            buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
            let boundary: number
            while ((boundary = buffer.indexOf('\n\n')) >= 0) {
              const frame = buffer.slice(0, boundary)
              buffer = buffer.slice(boundary + 2)
              if (/^event:\s*(connected|refresh|heartbeat)$/m.test(frame)) {
                if (!lifecycle.signal.aborted) setConnected(true)
                delay = 1000
                // The initial/reconnected stream is registered before this authoritative fetch.
                void refreshState()
              }
            }
          }
        } catch { /* Reconnect and fetch authoritative state; drafts remain in their components. */ }
        finally {
          window.clearTimeout(watchdog)
          lifecycle.signal.removeEventListener('abort', stop)
          connection.abort()
          if (!lifecycle.signal.aborted) setConnected(false)
        }
        if (lifecycle.signal.aborted) break
        await new Promise<void>(resolve => {
          const finish = () => { window.clearTimeout(timer); lifecycle.signal.removeEventListener('abort', finish); resolve() }
          const timer = window.setTimeout(finish, delay)
          lifecycle.signal.addEventListener('abort', finish, { once: true })
        })
        delay = Math.min(delay * 2, 15000)
      }
    }
    void stream()
    return () => { lifecycle.abort() }
  }, [token, onExpired])

  return { state: loaded?.token === token ? loaded?.state ?? null : null, connected, refresh }
}
