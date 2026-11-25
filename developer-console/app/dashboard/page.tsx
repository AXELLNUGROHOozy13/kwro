'use client';

import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, api, logout } from '@/lib/api';

interface Stats {
  administrators: { total: number; active: number; suspended: number };
  students: number;
  exams: number;
  activationCodes: { total: number; ACTIVE: number; EXPIRED: number; EXHAUSTED: number; REVOKED: number };
  recentActivity: { id: string; actorType: string; action: string; createdAt: string }[];
  system: { database: string };
}
interface Code {
  id: string; hint: string; label: string | null; status: 'ACTIVE' | 'EXPIRED' | 'EXHAUSTED' | 'REVOKED';
  usedCount: number; maxUses: number; expiresAt: string | null; createdAt: string;
  usedBy: { id: string; email: string; schoolName: string }[];
}
interface Admin { id: string; email: string; name: string; schoolName: string; status: 'ACTIVE' | 'SUSPENDED'; createdAt: string; _count: { students: number; exams: number } }

const fmt = (d: string) => new Date(d).toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short' });
const codeBadge = { ACTIVE: ['badge-ok', 'Aktif'], EXPIRED: ['badge-warn', 'Kedaluwarsa'], EXHAUSTED: ['badge-muted', 'Habis dipakai'], REVOKED: ['badge-bad', 'Dicabut'] } as const;

export default function Dashboard() {
  const router = useRouter();
  const [stats, setStats] = useState<Stats | null>(null);
  const [codes, setCodes] = useState<Code[]>([]);
  const [admins, setAdmins] = useState<Admin[]>([]);
  const [error, setError] = useState('');
  const [fresh, setFresh] = useState<string | null>(null); // plaintext code, shown once
  const [copied, setCopied] = useState(false);
  const [form, setForm] = useState({ label: '', maxUses: 1, expiresInDays: 30 });

  const guard = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) router.replace('/login');
    else setError(e instanceof Error ? e.message : 'Terjadi kesalahan.');
  }, [router]);

  const load = useCallback(async () => {
    try {
      const [s, c, a] = await Promise.all([
        api<Stats>('/developer/stats'),
        api<{ data: Code[] }>('/developer/activation-codes?pageSize=50'),
        api<{ data: Admin[] }>('/developer/administrators?pageSize=50'),
      ]);
      setStats(s); setCodes(c.data); setAdmins(a.data); setError('');
    } catch (e) { guard(e); }
  }, [guard]);

  useEffect(() => { void load(); }, [load]);

  async function createCode(e: React.FormEvent) {
    e.preventDefault();
    try {
      const r = await api<{ code: string }>('/developer/activation-codes', {
        method: 'POST',
        body: { label: form.label || undefined, maxUses: form.maxUses, expiresInDays: form.expiresInDays },
      });
      setFresh(r.code); setCopied(false); await load();
    } catch (err) { guard(err); }
  }

  async function act(path: string, question: string) {
    if (!window.confirm(question)) return;
    try { await api(path, { method: 'POST' }); await load(); } catch (err) { guard(err); }
  }

  async function copy() {
    if (!fresh) return;
    await navigator.clipboard.writeText(fresh).catch(() => undefined);
    setCopied(true);
  }

  const cards: [string, string | number][] = stats ? [
    ['Administrator aktif', stats.administrators.active],
    ['Administrator ditangguhkan', stats.administrators.suspended],
    ['Siswa', stats.students],
    ['Ujian', stats.exams],
    ['Kode aktivasi aktif', stats.activationCodes.ACTIVE],
    ['Database', stats.system.database === 'ok' ? 'Terhubung' : 'Bermasalah'],
  ] : [];

  return (
    <main className="mx-auto max-w-6xl p-4 sm:p-6 grid gap-5">
      <header className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold">KAWREXAM Developer Console</h1>
          <p className="muted text-sm">Kelola kode aktivasi dan akses administrator.</p>
        </div>
        <button className="btn" onClick={async () => { await logout(); router.replace('/login'); }}>Keluar</button>
      </header>

      {error && <p role="alert" className="glass p-3 text-sm" style={{ color: 'var(--danger)' }}>{error}</p>}

      <section aria-label="Ringkasan" className="grid grid-cols-2 lg:grid-cols-6 gap-3">
        {!stats && !error && <p className="muted col-span-full">Memuat…</p>}
        {cards.map(([label, value]) => (
          <div key={label} className="glass p-4">
            <p className="muted text-xs">{label}</p>
            <p className="num text-2xl font-bold mt-1">{value}</p>
          </div>
        ))}
      </section>

      <section className="glass p-4 sm:p-5 grid gap-4" aria-labelledby="new-code">
        <h2 id="new-code" className="text-lg font-semibold">Buat kode aktivasi</h2>
        <form onSubmit={createCode} className="grid sm:grid-cols-4 gap-3 items-end">
          <div className="sm:col-span-2">
            <label className="label" htmlFor="label">Label (opsional, misal nama sekolah)</label>
            <input id="label" className="input" maxLength={100} value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="uses">Batas pemakaian</label>
            <input id="uses" className="input num" type="number" min={1} max={500} value={form.maxUses} onChange={(e) => setForm({ ...form, maxUses: Number(e.target.value) })} />
          </div>
          <div>
            <label className="label" htmlFor="days">Berlaku (hari)</label>
            <input id="days" className="input num" type="number" min={1} max={365} value={form.expiresInDays} onChange={(e) => setForm({ ...form, expiresInDays: Number(e.target.value) })} />
          </div>
          <div className="sm:col-span-4"><button className="btn btn-primary">Buat kode</button></div>
        </form>

        {fresh && (
          <div role="status" className="glass p-4 grid gap-2" style={{ borderColor: 'var(--accent)' }}>
            <p className="text-sm">Salin kode ini sekarang. Kode hanya muncul sekali dan tidak bisa dilihat lagi.</p>
            <p className="code-reveal">{fresh}</p>
            <div className="flex gap-2">
              <button className="btn btn-sm" onClick={copy}>{copied ? 'Tersalin' : 'Salin kode'}</button>
              <button className="btn btn-sm" onClick={() => setFresh(null)}>Sudah saya simpan</button>
            </div>
          </div>
        )}
      </section>

      <section className="glass p-4 sm:p-5" aria-labelledby="codes">
        <h2 id="codes" className="text-lg font-semibold mb-2">Kode aktivasi</h2>
        {codes.length === 0 ? <p className="muted text-sm">Belum ada kode. Buat kode pertama di atas.</p> : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead><tr><th>Kode</th><th>Label</th><th>Status</th><th>Terpakai</th><th>Berlaku sampai</th><th>Dipakai oleh</th><th></th></tr></thead>
              <tbody>
                {codes.map((c) => (
                  <tr key={c.id}>
                    <td className="num">KRX-••••-{c.hint}</td>
                    <td>{c.label ?? '-'}</td>
                    <td><span className={`badge ${codeBadge[c.status][0]}`}>{codeBadge[c.status][1]}</span></td>
                    <td className="num">{c.usedCount}/{c.maxUses}</td>
                    <td>{c.expiresAt ? fmt(c.expiresAt) : 'Tanpa batas'}</td>
                    <td>{c.usedBy.length ? c.usedBy.map((u) => u.schoolName).join(', ') : '-'}</td>
                    <td>{c.status === 'ACTIVE' && <button className="btn btn-sm btn-danger" onClick={() => act(`/developer/activation-codes/${c.id}/revoke`, 'Cabut kode ini? Kode tidak bisa dipakai lagi.')}>Cabut</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="glass p-4 sm:p-5" aria-labelledby="admins">
        <h2 id="admins" className="text-lg font-semibold mb-2">Administrator</h2>
        {admins.length === 0 ? <p className="muted text-sm">Belum ada administrator. Bagikan kode aktivasi agar sekolah bisa mendaftar.</p> : (
          <div className="overflow-x-auto">
            <table className="table">
              <thead><tr><th>Nama</th><th>Sekolah</th><th>Email</th><th>Siswa</th><th>Ujian</th><th>Status</th><th></th></tr></thead>
              <tbody>
                {admins.map((a) => (
                  <tr key={a.id}>
                    <td>{a.name}</td><td>{a.schoolName}</td><td>{a.email}</td>
                    <td className="num">{a._count.students}</td><td className="num">{a._count.exams}</td>
                    <td><span className={`badge ${a.status === 'ACTIVE' ? 'badge-ok' : 'badge-bad'}`}>{a.status === 'ACTIVE' ? 'Aktif' : 'Ditangguhkan'}</span></td>
                    <td>
                      {a.status === 'ACTIVE'
                        ? <button className="btn btn-sm btn-danger" onClick={() => act(`/developer/administrators/${a.id}/suspend`, `Tangguhkan ${a.name}? Akses langsung diputus.`)}>Tangguhkan</button>
                        : <button className="btn btn-sm" onClick={() => act(`/developer/administrators/${a.id}/activate`, `Aktifkan kembali ${a.name}?`)}>Aktifkan</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="glass p-4 sm:p-5" aria-labelledby="recent">
        <h2 id="recent" className="text-lg font-semibold mb-2">Aktivitas terbaru</h2>
        {!stats || stats.recentActivity.length === 0 ? <p className="muted text-sm">Belum ada aktivitas.</p> : (
          <ul className="grid gap-1 text-sm">
            {stats.recentActivity.map((r) => (
              <li key={r.id} className="flex justify-between gap-3"><span>{r.action}</span><span className="muted num">{fmt(r.createdAt)}</span></li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
