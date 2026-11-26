'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { completeMfa, login } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [code, setCode] = useState('');

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (mfaToken) await completeMfa(mfaToken, code.trim());
      else {
        const challenge = await login(email, password);
        if (challenge) { setMfaToken(challenge.mfaToken); setPassword(''); setBusy(false); return; }
      }
      router.replace('/dashboard');
    } catch (err) {
      // An expired verification (5 minutes) sends the person back to the password step.
      if (mfaToken && err instanceof Error && (err as { code?: string }).code === 'MFA_EXPIRED') { setMfaToken(null); setCode(''); }
      setError(err instanceof Error ? err.message : 'Gagal masuk.');
      setBusy(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center p-4">
      <form onSubmit={onSubmit} className="glass w-full max-w-sm p-6 grid gap-4" aria-labelledby="title">
        <div>
          <h1 id="title" className="text-2xl font-bold">KAWREXAM</h1>
          <p className="muted text-sm">Masuk sebagai administrator sekolah</p>
        </div>
        {mfaToken ? (
          <div>
            <label className="label" htmlFor="code">Kode verifikasi</label>
            <input id="code" className="input" autoComplete="one-time-code" autoFocus required value={code} onChange={(e) => setCode(e.target.value)} />
            <p className="muted text-sm mt-1">Buka aplikasi authenticator dan masukkan kode 6 angka, atau pakai salah satu kode pemulihan.</p>
          </div>
        ) : (
          <>
            <div>
              <label className="label" htmlFor="email">Email</label>
              <input id="email" className="input" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div>
              <label className="label" htmlFor="password">Kata sandi</label>
              <input id="password" className="input" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
          </>
        )}
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Memproses…' : mfaToken ? 'Verifikasi' : 'Masuk'}</button>
        <p className="muted text-sm text-center">
          Belum punya akun? <Link href="/register" className="underline">Daftar dengan kode aktivasi</Link>
        </p>
      </form>
    </main>
  );
}
