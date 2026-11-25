'use client';

import { useCallback, useEffect, useState } from 'react';
import { Modal } from '@/components/Modal';
import { useApiGuard } from '@/components/Shell';
import { api } from '@/lib/api';
import { downloadText, studentsFromCsv, toCsv } from '@/lib/csv';

interface Student {
  id: string; name: string; loginCode: string; status: 'ACTIVE' | 'DISABLED'; mustChangePassword: boolean;
  classId: string | null; class: { id: string; name: string } | null;
}
interface ClassRow { id: string; name: string }
const PAGE_SIZE = 50;

export default function StudentsPage() {
  const [rows, setRows] = useState<Student[] | null>(null);
  const [total, setTotal] = useState(0);
  const [classes, setClasses] = useState<ClassRow[]>([]);
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState('');
  const [q, setQ] = useState('');
  const [qLive, setQLive] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const guard = useApiGuard(setError);

  const loadClasses = useCallback(() => {
    api<{ data: ClassRow[] }>('/classes').then((r) => setClasses(r.data)).catch(guard);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const load = useCallback(() => {
    const p = new URLSearchParams({ page: String(page), pageSize: String(PAGE_SIZE) });
    if (filter === 'none') p.set('unassigned', 'true');
    else if (filter) p.set('classId', filter);
    if (q) p.set('q', q);
    api<{ data: Student[]; meta: { total: number } }>(`/students?${p}`)
      .then((r) => { setRows(r.data); setTotal(r.meta.total); setError(''); })
      .catch(guard);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, filter, q]);

  useEffect(loadClasses, [loadClasses]);
  useEffect(load, [load]);
  useEffect(() => { const t = setTimeout(() => { setPage(1); setQ(qLive.trim()); }, 300); return () => clearTimeout(t); }, [qLive]);

  async function patch(s: Student, body: object) {
    try { await api(`/students/${s.id}`, { method: 'PATCH', body }); load(); } catch (err) { guard(err); }
  }
  async function reset(s: Student) {
    if (!window.confirm(`Atur ulang kata sandi ${s.name}? Kata sandinya kembali sama dengan kode login ${s.loginCode}.`)) return;
    try { await api(`/students/${s.id}/reset-password`, { method: 'POST' }); setNotice(`Kata sandi ${s.name} sudah diatur ulang ke kode login.`); load(); } catch (err) { guard(err); }
  }
  async function remove(s: Student) {
    if (!window.confirm(`Hapus ${s.name}? Tindakan ini tidak bisa dibatalkan.`)) return;
    try { await api(`/students/${s.id}`, { method: 'DELETE' }); load(); } catch (err) { guard(err); }
  }
  function exportCodes() {
    downloadText('kode-login-siswa.csv', toCsv([['Nama', 'Kelas', 'Kode login'], ...(rows ?? []).map((s) => [s.name, s.class?.name ?? '', s.loginCode])]));
  }

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="h-page">Siswa</h1>
        <div className="flex gap-2">
          <button className="btn" onClick={() => setImporting(true)}>Impor CSV</button>
          <button className="btn btn-primary" onClick={() => setAdding(true)}>Tambah siswa</button>
        </div>
      </div>
      {error && <p role="alert" className="alert alert-bad">{error}</p>}
      {notice && <p role="status" className="alert alert-ok">{notice}</p>}

      <div className="glass p-4 flex flex-wrap gap-3 items-end">
        <div className="grow max-w-xs">
          <label className="label" htmlFor="q">Cari nama atau kode login</label>
          <input id="q" className="input" value={qLive} onChange={(e) => setQLive(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="cf">Kelas</label>
          <select id="cf" className="input" value={filter} onChange={(e) => { setPage(1); setFilter(e.target.value); }}>
            <option value="">Semua kelas</option>
            <option value="none">Tanpa kelas</option>
            {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <button className="btn" onClick={exportCodes} disabled={!rows?.length}>Unduh kode login (halaman ini)</button>
      </div>

      <section className="glass p-2 scroll-x">
        {rows === null ? <p className="p-4 muted">Memuat…</p> : rows.length === 0 ? (
          <p className="p-4 muted">{q || filter ? 'Tidak ada siswa yang cocok dengan filter.' : 'Belum ada siswa. Tambahkan satu per satu atau impor dari CSV.'}</p>
        ) : (
          <table className="table">
            <thead><tr><th>Nama</th><th>Kelas</th><th>Kode login</th><th>Status</th><th /></tr></thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id}>
                  <td className="font-semibold">{s.name}</td>
                  <td>
                    <select className="input input-sm" aria-label={`Kelas ${s.name}`} value={s.classId ?? ''} onChange={(e) => patch(s, { classId: e.target.value || null })}>
                      <option value="">Tanpa kelas</option>
                      {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </td>
                  <td className="mono">{s.loginCode}</td>
                  <td>
                    <span className={`badge ${s.status === 'ACTIVE' ? 'badge-ok' : 'badge-bad'}`}>{s.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}</span>
                    {s.mustChangePassword && <span className="muted text-xs block">Belum ganti sandi</span>}
                  </td>
                  <td className="text-right whitespace-nowrap">
                    <button className="btn btn-sm" onClick={() => reset(s)}>Reset sandi</button>{' '}
                    <button className="btn btn-sm" onClick={() => patch(s, { status: s.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>{s.status === 'ACTIVE' ? 'Nonaktifkan' : 'Aktifkan'}</button>{' '}
                    <button className="btn btn-sm btn-danger" onClick={() => remove(s)}>Hapus</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <div className="flex items-center justify-between muted text-sm">
        <span className="num">{total} siswa</span>
        <div className="flex items-center gap-2">
          <button className="btn btn-sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>Sebelumnya</button>
          <span className="num">{page} / {pages}</span>
          <button className="btn btn-sm" disabled={page >= pages} onClick={() => setPage(page + 1)}>Berikutnya</button>
        </div>
      </div>
      <p className="muted text-sm">Kata sandi awal siswa sama dengan kode login 7 digit. Siswa wajib menggantinya saat masuk pertama.</p>

      {adding && <AddStudent classes={classes} onClose={() => setAdding(false)} onDone={() => { setAdding(false); load(); loadClasses(); }} />}
      {importing && <ImportStudents onClose={() => { setImporting(false); load(); loadClasses(); }} />}
    </div>
  );
}

function AddStudent({ classes, onClose, onDone }: { classes: ClassRow[]; onClose: () => void; onDone: () => void }) {
  const [name, setName] = useState('');
  const [classId, setClassId] = useState('');
  const [error, setError] = useState('');
  const [created, setCreated] = useState<Student | null>(null);
  const guard = useApiGuard(setError);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try { setCreated(await api<Student>('/students', { method: 'POST', body: { name, classId: classId || null } })); } catch (err) { guard(err); }
  }
  if (created) {
    return (
      <Modal title="Siswa ditambahkan" onClose={onDone}>
        <p>{created.name}</p>
        <p className="muted text-sm mt-3">Kode login (juga kata sandi awal)</p>
        <p className="code-reveal">{created.loginCode}</p>
        <button className="btn btn-primary mt-4" onClick={onDone}>Selesai</button>
      </Modal>
    );
  }
  return (
    <Modal title="Tambah siswa" onClose={onClose}>
      <form onSubmit={submit} className="grid gap-4">
        <div>
          <label className="label" htmlFor="sname">Nama lengkap</label>
          <input id="sname" className="input" required minLength={2} maxLength={100} autoFocus value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="sclass">Kelas</label>
          <select id="sclass" className="input" value={classId} onChange={(e) => setClassId(e.target.value)}>
            <option value="">Tanpa kelas</option>
            {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <button className="btn btn-primary">Simpan</button>
      </form>
    </Modal>
  );
}

const SAMPLE = 'nama,kelas\nAndi Pratama,X IPA 1\nSiti Rahma,X IPA 1\nBudi Santoso,X IPS 2\n';

function ImportStudents({ onClose }: { onClose: () => void }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<Student[] | null>(null);
  const guard = useApiGuard(setError);
  const parsed = text.trim() ? studentsFromCsv(text) : null;

  async function run() {
    if (!parsed || parsed.errors.length || parsed.students.length === 0) return;
    setBusy(true);
    setError('');
    const done: Student[] = [];
    try {
      for (let i = 0; i < parsed.students.length; i += 100) {
        const r = await api<{ data: Student[] }>('/students/import', { method: 'POST', body: { students: parsed.students.slice(i, i + 100) } });
        done.push(...r.data);
      }
    } catch (err) { guard(err); }
    // Show whatever was created, even if a later batch failed, so no login code is lost.
    if (done.length) setResult(done);
    setBusy(false);
  }

  if (result) {
    return (
      <Modal title={`${result.length} siswa diimpor`} onClose={onClose}>
        {error && <p role="alert" className="alert alert-bad mb-3">Sebagian gagal: {error}</p>}
        <p className="muted text-sm mb-3">Unduh daftar kode login sekarang untuk dibagikan ke siswa. Kode juga selalu bisa dilihat di halaman Siswa.</p>
        <div className="flex gap-2">
          <button className="btn btn-primary" onClick={() => downloadText('kode-login-siswa.csv', toCsv([['Nama', 'Kelas', 'Kode login'], ...result.map((s) => [s.name, s.class?.name ?? '', s.loginCode])]))}>Unduh kode login</button>
          <button className="btn" onClick={onClose}>Selesai</button>
        </div>
      </Modal>
    );
  }
  return (
    <Modal title="Impor siswa dari CSV" onClose={onClose}>
      <div className="grid gap-4">
        <p className="muted text-sm">Kolom: <b>nama</b>, <b>kelas</b> (opsional). Kelas yang belum ada dibuat otomatis. Simpan file Excel sebagai CSV, lalu pilih file atau tempel isinya.</p>
        <div className="flex flex-wrap gap-2">
          <input type="file" accept=".csv,text/csv,text/plain" aria-label="File CSV" onChange={async (e) => { const f = e.target.files?.[0]; if (f) setText(await f.text()); }} />
          <button type="button" className="btn btn-sm" onClick={() => downloadText('template-siswa.csv', SAMPLE)}>Unduh template</button>
        </div>
        <textarea className="input mono" aria-label="Isi CSV" placeholder={SAMPLE} value={text} onChange={(e) => setText(e.target.value)} />
        {parsed && (
          <div>
            <p className="num">{parsed.students.length} siswa siap diimpor{parsed.errors.length > 0 && `, ${parsed.errors.length} baris bermasalah`}.</p>
            {parsed.errors.length > 0 && <ul className="alert alert-bad mt-2 text-sm grid gap-1">{parsed.errors.slice(0, 6).map((m) => <li key={m}>{m}</li>)}</ul>}
          </div>
        )}
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <button className="btn btn-primary" disabled={busy || !parsed || parsed.errors.length > 0 || parsed.students.length === 0} onClick={run}>{busy ? 'Mengimpor…' : 'Impor'}</button>
      </div>
    </Modal>
  );
}
