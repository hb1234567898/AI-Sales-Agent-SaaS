export function fetchRequestUrl(input: RequestInfo | URL) {
  return input instanceof Request ? input.url : String(input)
}

export function fetchRequestMethod(input: RequestInfo | URL, init?: RequestInit) {
  return input instanceof Request ? input.method : (init?.method ?? 'GET')
}

export async function fetchRequestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  if (input instanceof Request) return input.clone().json() as Promise<T>
  return JSON.parse(String(init?.body)) as T
}
