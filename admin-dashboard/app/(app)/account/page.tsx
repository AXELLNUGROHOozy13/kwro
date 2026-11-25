'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { api, logout } from '@/lib/api';
import { useApiGuard, useMe } from '@/components/Shell';

export default function AccountPage() {
  const me = useMe();
  const router = useRouter();
  const [f, setF] = useState({ currentPassword: '', newPassword: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const guard = useApiGuard(setError);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api('/me/password', { method: 'POST', body: f });
      await logout();
      router.replace('/login');
    } catch (err) { guard(err); setBusy(false); }
  }

  return (
    <div className="grid gap-5 max-w-lg">
      <h1 className="h-page">Akun</h1>
      <section className="glass p-5">
        <p className="font-semibold">{me?.name}</p>
        <p className="muted">{me?.email}</p>
        <p className="muted">{me?.schoolName}</p>
      </section>
      <form onSubmit={submit} className="glass p-5 grid gap-4">
        <h2 className="h-sec">Ganti kata sandi</h2>
        <div>
          <label className="label" htmlFor="cur">Kata sandi saat ini</label>
          <input id="cur" className="input" type="password" autoComplete="current-password" required value={f.currentPassword} onChange={(e) => setF({ ...f, currentPassword: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="new">Kata sandi baru (minimal 12 karakter)</label>
          <input id="new" className="input" type="password" autoComplete="new-password" required minLength={12} value={f.newPassword} onChange={(e) => setF({ ...f, newPassword: e.target.value })} />
        </div>
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <p className="muted text-sm">Setelah diganti, semua perangkat keluar dan Anda perlu masuk lagi.</p>
        <button className="btn btn-primary" disabled={busy}>Ganti kata sandi</button>
      </form>
      <button className="btn md:hidden" onClick={async () => { await logout(); router.replace('/login'); }}>Keluar</button>
    </div>
  );
}
