'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useApiGuard } from '@/components/Shell';

interface ClassRow { id: string; name: string; _count: { students: number; assignments: number } }

export default function ClassesPage() {
  const [rows, setRows] = useState<ClassRow[] | null>(null);
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null);
  const [error, setError] = useState('');
  const guard = useApiGuard(setError);

  const load = useCallback(() => {
    api<{ data: ClassRow[] }>('/classes').then((r) => { setRows(r.data); setError(''); }).catch(guard);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(load, [load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    try { await api('/classes', { method: 'POST', body: { name } }); setName(''); load(); } catch (err) { guard(err); }
  }
  async function rename(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    try { await api(`/classes/${editing.id}`, { method: 'PATCH', body: { name: editing.name } }); setEditing(null); load(); } catch (err) { guard(err); }
  }
  async function remove(c: ClassRow) {
    const note = c._count.students > 0 ? ` ${c._count.students} siswa akan tetap ada, tetapi tidak punya kelas.` : '';
    if (!window.confirm(`Hapus kelas ${c.name}?${note}`)) return;
    try { await api(`/classes/${c.id}`, { method: 'DELETE' }); load(); } catch (err) { guard(err); }
  }

  return (
    <div className="grid gap-5">
      <h1 className="h-page">Kelas</h1>
      {error && <p role="alert" className="alert alert-bad">{error}</p>}

      <form onSubmit={add} className="glass p-4 flex flex-wrap gap-3 items-end">
        <div className="grow max-w-sm">
          <label className="label" htmlFor="cname">Nama kelas baru</label>
          <input id="cname" className="input" required maxLength={60} placeholder="Contoh: X IPA 1" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <button className="btn btn-primary">Tambah kelas</button>
      </form>

      <section className="glass p-2 scroll-x">
        {rows === null ? <p className="p-4 muted">Memuat…</p> : rows.length === 0 ? (
          <p className="p-4 muted">Belum ada kelas. Tambahkan kelas pertama di atas, atau impor siswa lewat CSV dengan kolom kelas.</p>
        ) : (
          <table className="table">
            <thead><tr><th>Kelas</th><th>Siswa</th><th>Ujian</th><th /></tr></thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  <td className="font-semibold">
                    {editing?.id === c.id ? (
                      <form onSubmit={rename} className="flex gap-2">
                        <input className="input input-sm" autoFocus required maxLength={60} aria-label="Nama kelas" value={editing.name} onChange={(e) => setEditing({ id: c.id, name: e.target.value })} />
                        <button className="btn btn-sm btn-primary">Simpan</button>
                        <button type="button" className="btn btn-sm" onClick={() => setEditing(null)}>Batal</button>
                      </form>
                    ) : c.name}
                  </td>
                  <td className="num">{c._count.students}</td>
                  <td className="num">{c._count.assignments}</td>
                  <td className="text-right whitespace-nowrap">
                    <button className="btn btn-sm" onClick={() => setEditing({ id: c.id, name: c.name })}>Ubah nama</button>{' '}
                    <button className="btn btn-sm btn-danger" onClick={() => remove(c)}>Hapus</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
