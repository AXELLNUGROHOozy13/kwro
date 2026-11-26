'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { register } from '@/lib/api';
import { Seal } from '@/components/Seal';

export default function RegisterPage() {
  const router = useRouter();
  const [f, setF] = useState({ activationCode: '', schoolName: '', name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await register({ ...f, activationCode: f.activationCode.trim() });
      router.replace('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Pendaftaran gagal.');
      setBusy(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center p-4">
      <form onSubmit={onSubmit} className="glass w-full max-w-md p-6 grid gap-4" aria-labelledby="title">
        <div className="grid gap-3 justify-items-center text-center pb-1">
          <Seal large />
          <h1 id="title" className="text-2xl font-semibold">Daftar administrator</h1>
          <p className="muted text-sm">Kode aktivasi didapat dari developer KAWREXAM.</p>
        </div>
        <div>
          <label className="label" htmlFor="code">Kode aktivasi</label>
          <input id="code" className="input mono" required autoComplete="off" placeholder="KRX-XXXX-XXXX-XXXX-XXXX" value={f.activationCode} onChange={set('activationCode')} />
        </div>
        <div>
          <label className="label" htmlFor="school">Nama sekolah</label>
          <input id="school" className="input" required minLength={2} value={f.schoolName} onChange={set('schoolName')} />
        </div>
        <div>
          <label className="label" htmlFor="name">Nama Anda</label>
          <input id="name" className="input" required minLength={2} autoComplete="name" value={f.name} onChange={set('name')} />
        </div>
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" className="input" type="email" required autoComplete="username" value={f.email} onChange={set('email')} />
        </div>
        <div>
          <label className="label" htmlFor="password">Kata sandi (minimal 12 karakter)</label>
          <input id="password" className="input" type="password" required minLength={12} autoComplete="new-password" value={f.password} onChange={set('password')} />
        </div>
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Memproses…' : 'Buat akun'}</button>
        <p className="muted text-sm text-center">
          Sudah punya akun? <Link href="/login" className="underline">Masuk</Link>
        </p>
      </form>
    </main>
  );
}
