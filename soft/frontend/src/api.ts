export class ApiError extends Error {
  constructor(public code: string, message: string) {
    super(message)
  }
}

export async function api<T>(path: string, token?: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new ApiError(payload.code ?? 'REQUEST_FAILED', payload.message ?? 'Операция не выполнена')
  }
  return payload as T
}
