'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { createContext, useContext, useEffect, useState } from 'react';
import { ApiError, api, logout } from '@/lib/api';
import { Seal } from '@/components/Seal';

export interface Me { id: string; email: string; name: string; schoolName: string }
const MeContext = createContext<Me | null>(null);
export const useMe = () => useContext(MeContext);

const NAV = [
  { href: '/dashboard', label: 'Ringkasan' },
  { href: '/classes', label: 'Kelas' },
  { href: '/students', label: 'Siswa' },
  { href: '/exams', label: 'Ujian' },
  { href: '/security', label: 'Keamanan' },
  { href: '/account', label: 'Akun' },
];

export function Shell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    api<Me>('/me')
      .then(setMe)
      .catch((e) => {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) router.replace('/login');
      });
  }, [router]);

  async function signOut() {
    await logout();
    router.replace('/login');
  }

  if (!me) return <main className="min-h-screen grid place-items-center muted">Memuat…</main>;

  return (
    <MeContext.Provider value={me}>
      <div className="shell">
        <aside className="sidebar">
          <nav className="glass" aria-label="Menu utama">
            <div className="brand">
              <Seal />
              <div className="min-w-0">
                <p className="brand-name leading-tight">KAWREXAM</p>
                <p className="muted text-sm truncate">{me.schoolName}</p>
              </div>
            </div>
            {NAV.map((n) => (
              <Link key={n.href} href={n.href} className="nav-link" aria-current={path === n.href || path.startsWith(n.href + '/') ? 'page' : undefined}>
                {n.label}
              </Link>
            ))}
            <div className="spacer flex-1" />
            <button className="btn btn-sm hidden md:block" onClick={signOut}>Keluar</button>
          </nav>
        </aside>
        <main className="content">{children}</main>
      </div>
    </MeContext.Provider>
  );
}

/** Shared handler: bounce to login on an expired session, otherwise surface the message. */
export function useApiGuard(setError: (m: string) => void) {
  const router = useRouter();
  return (e: unknown) => {
    if (e instanceof ApiError && e.status === 401) router.replace('/login');
    else setError(e instanceof Error ? e.message : 'Terjadi kesalahan.');
  };
}
