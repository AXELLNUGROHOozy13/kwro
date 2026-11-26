'use client';

import { useCallback, useEffect, useState } from 'react';
import { useApiGuard } from '@/components/Shell';
import { api } from '@/lib/api';

interface Status { mfaEnabled: boolean; recoveryCodesLeft: number }

// Two-step verification (authenticator app) for the administrator account.
export default function SecurityPage() {
  const [st, setSt] = useState<Status | null>(null);
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [code, setCode] = useState('');
  const [pw, setPw] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const guard = useApiGuard(setMsg);

  const load = useCallback(() => { api<Status>('/security').then(setSt).catch(guard); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);
  useEffect(load, [load]);

  async function run(fn: () => Promise<void>) {
    setBusy(true); setMsg('');
    try { await fn(); } catch (e) { guard(e); }
    setBusy(false);
  }

  if (!st) return <p className="muted">{msg || 'Memuat…'}</p>;
  return (
    <div className="grid gap-4" style={{ maxWidth: '40rem' }}>
      <h1 className="text-2xl font-bold">Keamanan akun</h1>

      <section className="glass p-5 grid gap-3" aria-labelledby="mfa">
        <h2 id="mfa" className="font-semibold">Verifikasi dua langkah (aplikasi authenticator)</h2>
        <p className="muted">
          Status: <b>{st.mfaEnabled ? 'Aktif' : 'Belum aktif'}</b>
          {st.mfaEnabled && ` · ${st.recoveryCodesLeft} kode pemulihan tersisa`}
        </p>

        {codes && (
          <div role="status" className="p-3 rounded-xl" style={{ border: '1px solid var(--glass-border)' }}>
            <b>Simpan kode pemulihan ini sekarang. Tidak akan ditampilkan lagi.</b>
            <p className="muted text-sm">Tiap kode hanya bisa dipakai sekali, kalau HP authenticator hilang.</p>
            <pre className="num mt-2" style={{ whiteSpace: 'pre-wrap' }}>{codes.join('\n')}</pre>
            <button className="btn btn-sm mt-2" onClick={() => setCodes(null)}>Sudah saya simpan</button>
          </div>
        )}

        {!st.mfaEnabled && !setup && (
          <button className="btn btn-primary" style={{ justifySelf: 'start' }} disabled={busy}
            onClick={() => run(async () => setSetup(await api('/security/mfa/setup', { method: 'POST' })))}>Aktifkan</button>
        )}

        {!st.mfaEnabled && setup && (
          <div className="grid gap-3">
            <p>1. Di aplikasi authenticator (Google Authenticator, Microsoft Authenticator, dll.), pilih tambah akun lalu masukkan kunci ini secara manual:</p>
            <code className="num p-2 rounded-lg" style={{ background: 'var(--glass-solid)', wordBreak: 'break-all' }}>{setup.secret}</code>
            <p className="muted text-sm">Di HP, <a className="underline" href={setup.otpauthUri}>ketuk di sini</a> untuk membukanya langsung di aplikasi authenticator.</p>
            <p>2. Masukkan kode 6 angka yang muncul:</p>
            <div className="flex gap-2 flex-wrap">
              <input className="input" style={{ maxWidth: '10rem' }} inputMode="numeric" autoComplete="one-time-code" maxLength={6} aria-label="Kode 6 angka" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
              <button className="btn btn-primary" disabled={busy || code.length !== 6}
                onClick={() => run(async () => { const r = await api<{ recoveryCodes: string[] }>('/security/mfa/enable', { method: 'POST', body: { code } }); setCodes(r.recoveryCodes); setSetup(null); setCode(''); load(); })}>Verifikasi dan aktifkan</button>
            </div>
          </div>
        )}

        {st.mfaEnabled && (
          <details>
            <summary className="cursor-pointer font-semibold">Matikan verifikasi dua langkah</summary>
            <div className="grid gap-2 mt-3">
              <input className="input" type="password" placeholder="Kata sandi" autoComplete="current-password" value={pw} onChange={(e) => setPw(e.target.value)} />
              <input className="input" placeholder="Kode authenticator atau kode pemulihan" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} />
              <button className="btn btn-danger" style={{ justifySelf: 'start' }} disabled={busy || !pw || !code}
                onClick={() => run(async () => { await api('/security/mfa/disable', { method: 'POST', body: { password: pw, code } }); setPw(''); setCode(''); load(); })}>Matikan</button>
            </div>
          </details>
        )}
        {msg && <p role="alert" className="field-error">{msg}</p>}
      </section>
    </div>
  );
}
