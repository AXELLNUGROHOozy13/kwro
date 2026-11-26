// Access token lives only in memory. The refresh token is an httpOnly cookie that the browser sends
// automatically, so reloading the page silently restores the session.
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details?: unknown) {
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
  const details = data?.error?.details;
  let message: string = data?.error?.message ?? 'Terjadi kesalahan. Coba lagi.';
  // Show the first field-level validation message when the server provides one.
  if (details && typeof details === 'object') {
    const first = Object.values(details as Record<string, string[]>).flat()[0];
    if (typeof first === 'string') message = first;
  }
  return new ApiError(res.status, data?.error?.code ?? 'ERROR', message, details);
}

async function authPost(path: string, body: unknown): Promise<void> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw await parseError(res);
  accessToken = (await res.json()).accessToken as string;
}

/** Returns { mfaToken } when two-step verification is on (finish with completeMfa), otherwise signs in and returns null. */
export async function login(email: string, password: string): Promise<{ mfaToken: string } | null> {
  const res = await fetch('/api/v1/auth/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await parseError(res);
  const data = await res.json();
  if (data.mfaRequired) return { mfaToken: data.mfaToken as string };
  accessToken = data.accessToken as string;
  return null;
}

/** Second step: a 6-digit authenticator code or a one-time recovery code. */
export const completeMfa = (mfaToken: string, code: string) => authPost('/api/v1/auth/admin/login/mfa', { mfaToken, code });
export const register = (input: { activationCode: string; email: string; name: string; schoolName: string; password: string }) =>
  authPost('/api/v1/auth/admin/register', input);

export async function logout(): Promise<void> {
  await fetch('/api/v1/auth/logout', { method: 'POST' }).catch(() => undefined);
  accessToken = null;
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}, retry = true): Promise<T> {
  if (!accessToken && !(await refreshSession())) throw new ApiError(401, 'UNAUTHORIZED', 'Sesi berakhir. Masuk lagi.');
  const res = await fetch(`/api/v1/admin${path}`, {
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
