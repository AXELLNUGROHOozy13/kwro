'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { login } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(email, password);
      router.replace('/dashboard');
    } catch (err) {
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
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" className="input" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="password">Kata sandi</label>
          <input id="password" className="input" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Memproses…' : 'Masuk'}</button>
        <p className="muted text-sm text-center">
          Belum punya akun? <Link href="/register" className="underline">Daftar dengan kode aktivasi</Link>
        </p>
      </form>
    </main>
  );
}
