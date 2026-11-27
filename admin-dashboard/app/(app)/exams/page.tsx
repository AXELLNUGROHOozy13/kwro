'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Modal } from '@/components/Modal';
import { useApiGuard } from '@/components/Shell';
import { api } from '@/lib/api';
import { EXAM_STATUS, fmtDate } from '@/lib/format';

interface ExamRow {
  id: string; title: string; status: string; mode: string; durationMinutes: number; startsAt: string | null;
  _count: { questions: number; assignments: number; sessions: number };
}

export default function ExamsPage() {
  const router = useRouter();
  const [rows, setRows] = useState<ExamRow[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [f, setF] = useState({ title: '', durationMinutes: 60, mode: 'NATIVE', webUrl: '' });
  const [error, setError] = useState('');
  const guard = useApiGuard(setError);

  useEffect(() => {
    api<{ data: ExamRow[] }>('/exams').then((r) => setRows(r.data)).catch(guard);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    try {
      const exam = await api<{ id: string }>('/exams', { method: 'POST', body: { title: f.title, durationMinutes: f.durationMinutes, mode: f.mode, ...(f.mode === 'WEB' ? { webUrl: f.webUrl.trim() } : {}) } });
      router.push(`/exams/${exam.id}`);
    } catch (err) { guard(err); }
  }

  return (
    <div className="grid gap-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="h-page">Ujian</h1>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>Buat ujian</button>
      </div>
      {error && !creating && <p role="alert" className="alert alert-bad">{error}</p>}

      <section className="glass p-2 scroll-x">
        {rows === null ? <p className="p-4 muted">Memuat…</p> : rows.length === 0 ? (
          <p className="p-4 muted">Belum ada ujian. Buat ujian pertama, lalu isi soal dan pilih kelas pesertanya.</p>
        ) : (
          <table className="table">
            <thead><tr><th>Judul</th><th>Status</th><th>Soal</th><th>Kelas</th><th>Durasi</th><th>Mulai</th></tr></thead>
            <tbody>
              {rows.map((x) => {
                const [cls, label] = EXAM_STATUS[x.status] ?? ['badge-muted', x.status];
                return (
                  <tr key={x.id}>
                    <td className="font-semibold"><Link href={`/exams/${x.id}`} className="underline">{x.title}</Link>{x.mode === 'WEB' && <span className="badge badge-muted ml-2">Web</span>}</td>
                    <td><span className={`badge ${cls}`}>{label}</span></td>
                    <td className="num">{x.mode === 'WEB' ? '–' : x._count.questions}</td>
                    <td className="num">{x._count.assignments}</td>
                    <td className="num">{x.durationMinutes} menit</td>
                    <td className="muted">{fmtDate(x.startsAt)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>

      {creating && (
        <Modal title="Buat ujian" onClose={() => setCreating(false)}>
          <form onSubmit={create} className="grid gap-4">
            <div>
              <label className="label" htmlFor="title">Judul ujian</label>
              <input id="title" className="input" required minLength={3} maxLength={150} autoFocus value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="mode">Jenis ujian</label>
              <select id="mode" className="input" value={f.mode} onChange={(e) => setF({ ...f, mode: e.target.value })}>
                <option value="NATIVE">Soal di KawRexam</option>
                <option value="WEB">Ujian web (browser ujian terkunci)</option>
              </select>
            </div>
            {f.mode === 'WEB' && (
              <div>
                <label className="label" htmlFor="url">Alamat situs ujian</label>
                <input id="url" className="input" type="url" required placeholder="https://" pattern="https://.*" value={f.webUrl} onChange={(e) => setF({ ...f, webUrl: e.target.value })} />
                <p className="muted text-sm mt-1">Siswa membukanya di dalam KawRexam yang terkunci. Situs tambahan bisa diatur setelah ujian dibuat.</p>
              </div>
            )}
            <div>
              <label className="label" htmlFor="dur">Durasi (menit)</label>
              <input id="dur" className="input" type="number" required min={1} max={600} value={f.durationMinutes} onChange={(e) => setF({ ...f, durationMinutes: Number(e.target.value) })} />
            </div>
            {error && <p role="alert" className="alert alert-bad">{error}</p>}
            <button className="btn btn-primary">{f.mode === 'WEB' ? 'Buat ujian web' : 'Buat dan isi soal'}</button>
          </form>
        </Modal>
      )}
    </div>
  );
}
