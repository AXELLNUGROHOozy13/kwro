// Access token lives only in memory (not localStorage). The refresh token is an httpOnly cookie
// the browser sends automatically, so a page reload silently restores the session.
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

function refreshSession(): Promise<boolean> {
  refreshing ??= fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  })
    .then(async (r) => {
      if (!r.ok) return false;
      accessToken = (await r.json()).accessToken as string;
      return true;
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

async function parseError(res: Response): Promise<ApiError> {
  const data = await res.json().catch(() => null);
  return new ApiError(res.status, data?.error?.code ?? 'ERROR', data?.error?.message ?? 'Terjadi kesalahan. Coba lagi.');
}

export async function login(email: string, password: string): Promise<void> {
  const res = await fetch('/api/v1/auth/developer/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await parseError(res);
  accessToken = (await res.json()).accessToken as string;
}

export async function logout(): Promise<void> {
  await fetch('/api/v1/auth/logout', { method: 'POST' }).catch(() => undefined);
  accessToken = null;
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}, retry = true): Promise<T> {
  if (!accessToken && !(await refreshSession())) throw new ApiError(401, 'UNAUTHORIZED', 'Sesi berakhir. Masuk lagi.');
  const res = await fetch(`/api/v1${path}`, {
    method: init.method ?? 'GET',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  if (res.status === 401 && retry) {
    accessToken = null;
    if (await refreshSession()) return api<T>(path, init, false);
  }
  if (res.status === 204) return undefined as T;
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as T;
}
