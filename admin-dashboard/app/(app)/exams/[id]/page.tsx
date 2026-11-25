'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { Modal } from '@/components/Modal';
import { QuestionForm, emptyDraft, type QuestionDraft } from '@/components/QuestionForm';
import { useApiGuard } from '@/components/Shell';
import { api } from '@/lib/api';
import { QUESTION_TEMPLATE, downloadText, questionsFromCsv, toCsv } from '@/lib/csv';
import { EXAM_STATUS, QUESTION_TYPES, SESSION_STATUS, fmtDate, fromLocalInput, toLocalInput } from '@/lib/format';

interface Option { id: string; text: string; isCorrect: boolean }
interface Question { id: string; type: string; text: string; weight: number; explanation: string | null; acceptedAnswers: string[]; options: Option[] }
interface Exam {
  id: string; title: string; description: string | null; durationMinutes: number; startsAt: string | null; endsAt: string | null;
  kkm: number | null; shuffleQuestions: boolean; shuffleOptions: boolean; resultVisibility: string; offlineMode: boolean;
  scoreAdjustmentFactor: number; scoreMin: number; scoreMax: number; status: string; supervisorPin: string | null;
  questions: Question[]; assignments: { classId: string }[]; _count: { sessions: number };
}
interface ClassRow { id: string; name: string; _count: { students: number } }
interface Participant {
  studentId: string; name: string; className: string | null; status: string; warningCount: number;
  finishedAt: string | null; finalScore: number | null; passed: boolean | null;
}
type Tab = 'questions' | 'settings' | 'classes' | 'results';

export default function ExamDetail() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [exam, setExam] = useState<Exam | null>(null);
  const [tab, setTab] = useState<Tab>('questions');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const guard = useApiGuard(setError);

  const load = useCallback(() => {
    api<Exam>(`/exams/${id}`).then((e) => { setExam(e); setError(''); }).catch(guard);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);
  useEffect(load, [load]);

  async function setStatus(status: string, question?: string) {
    if (question && !window.confirm(question)) return;
    try { await api(`/exams/${id}/status`, { method: 'POST', body: { status } }); setNotice(''); load(); } catch (err) { guard(err); }
  }
  async function remove() {
    if (!window.confirm('Hapus ujian ini beserta semua soalnya? Tindakan ini tidak bisa dibatalkan.')) return;
    try { await api(`/exams/${id}`, { method: 'DELETE' }); router.replace('/exams'); } catch (err) { guard(err); }
  }

  if (!exam) return <p className="muted">{error ? '' : 'Memuat…'}{error && <span role="alert" className="alert alert-bad">{error}</span>}</p>;

  const [badge, label] = EXAM_STATUS[exam.status] ?? ['badge-muted', exam.status];
  const locked = exam._count.sessions > 0;
  const tabs: [Tab, string][] = [['questions', `Soal (${exam.questions.length})`], ['settings', 'Pengaturan'], ['classes', `Kelas (${exam.assignments.length})`], ['results', 'Peserta & hasil']];

  return (
    <div className="grid gap-5">
      <div>
        <Link href="/exams" className="muted text-sm underline">Semua ujian</Link>
        <div className="flex flex-wrap items-center justify-between gap-3 mt-1">
          <div className="flex items-center gap-3 min-w-0">
            <h1 className="h-page">{exam.title}</h1>
            <span className={`badge ${badge}`}>{label}</span>
          </div>
          <div className="flex flex-wrap gap-2">
            {exam.status === 'DRAFT' && <>
              <button className="btn btn-primary" onClick={() => setStatus('ACTIVE', 'Mulai ujian sekarang? Siswa di kelas peserta bisa langsung mengerjakan.')}>Mulai sekarang</button>
              <button className="btn" onClick={() => setStatus('SCHEDULED')}>Jadwalkan</button>
            </>}
            {exam.status === 'SCHEDULED' && <>
              <button className="btn btn-primary" onClick={() => setStatus('ACTIVE', 'Mulai ujian sekarang?')}>Mulai sekarang</button>
              <button className="btn" onClick={() => setStatus('DRAFT')}>Kembalikan ke draf</button>
            </>}
            {exam.status === 'ACTIVE' && <button className="btn" onClick={() => setStatus('SUSPENDED', 'Jeda ujian? Siswa tidak bisa melanjutkan sampai ujian dilanjutkan.')}>Jeda</button>}
            {exam.status === 'SUSPENDED' && <button className="btn btn-primary" onClick={() => setStatus('ACTIVE')}>Lanjutkan</button>}
            {(exam.status === 'ACTIVE' || exam.status === 'SUSPENDED' || exam.status === 'SCHEDULED') && <button className="btn btn-danger" onClick={() => setStatus('ENDED', 'Akhiri ujian? Ujian yang sudah berakhir tidak bisa dibuka lagi.')}>Akhiri</button>}
            {!locked && <button className="btn btn-danger" onClick={remove}>Hapus</button>}
          </div>
        </div>
      </div>

      {error && <p role="alert" className="alert alert-bad">{error}</p>}
      {notice && <p role="status" className="alert alert-ok">{notice}</p>}
      {locked && <p className="alert alert-warn">Soal terkunci karena sudah ada siswa yang membuka ujian ini.</p>}

      <div className="tabs" role="tablist">
        {tabs.map(([k, t]) => <button key={k} role="tab" className="tab" aria-selected={tab === k} onClick={() => setTab(k)}>{t}</button>)}
      </div>

      {tab === 'questions' && <QuestionsTab exam={exam} locked={locked} reload={load} onError={guard} />}
      {tab === 'settings' && <SettingsTab exam={exam} reload={load} onSaved={() => setNotice('Pengaturan disimpan.')} onError={guard} />}
      {tab === 'classes' && <ClassesTab exam={exam} reload={load} onError={guard} />}
      {tab === 'results' && <ResultsTab exam={exam} onError={guard} />}
    </div>
  );
}

// ───────── Questions ─────────
function toDraft(q: Question): QuestionDraft {
  return {
    type: q.type, text: q.text, weight: q.weight, explanation: q.explanation ?? '',
    options: q.options.length ? q.options.map((o) => ({ text: o.text, isCorrect: o.isCorrect })) : emptyDraft().options.map((o) => ({ ...o })),
    acceptedAnswers: q.acceptedAnswers.join('\n'),
  };
}

function QuestionsTab({ exam, locked, reload, onError }: { exam: Exam; locked: boolean; reload: () => void; onError: (e: unknown) => void }) {
  const [editing, setEditing] = useState<{ id: string | null; draft: QuestionDraft } | null>(null);
  const [importing, setImporting] = useState(false);

  async function save(payload: object) {
    const path = editing?.id ? `/exams/${exam.id}/questions/${editing.id}` : `/exams/${exam.id}/questions`;
    await api(path, { method: editing?.id ? 'PUT' : 'POST', body: payload });
    setEditing(null);
    reload();
  }
  async function remove(q: Question) {
    if (!window.confirm('Hapus soal ini?')) return;
    try { await api(`/exams/${exam.id}/questions/${q.id}`, { method: 'DELETE' }); reload(); } catch (err) { onError(err); }
  }

  return (
    <div className="grid gap-4">
      {!locked && (
        <div className="flex gap-2">
          <button className="btn btn-primary" onClick={() => setEditing({ id: null, draft: emptyDraft() })}>Tambah soal</button>
          <button className="btn" onClick={() => setImporting(true)}>Impor CSV</button>
        </div>
      )}
      {exam.questions.length === 0 ? (
        <p className="glass p-5 muted">Belum ada soal. Tambahkan satu per satu atau impor dari CSV.</p>
      ) : (
        <ol className="grid gap-3">
          {exam.questions.map((q, i) => (
            <li key={q.id} className="glass p-4 grid gap-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="muted text-sm"><span className="num">Soal {i + 1}</span> · {QUESTION_TYPES[q.type]} · bobot <span className="num">{q.weight}</span></p>
                {!locked && (
                  <div className="flex gap-2">
                    <button className="btn btn-sm" onClick={() => setEditing({ id: q.id, draft: toDraft(q) })}>Ubah</button>
                    <button className="btn btn-sm btn-danger" onClick={() => remove(q)}>Hapus</button>
                  </div>
                )}
              </div>
              <p className="whitespace-pre-wrap">{q.text}</p>
              {q.options.length > 0 && (
                <ul className="grid gap-1 text-sm">
                  {q.options.map((o, j) => (
                    <li key={o.id} className={o.isCorrect ? 'font-semibold' : 'muted'} style={o.isCorrect ? { color: 'var(--ok)' } : undefined}>
                      {String.fromCharCode(65 + j)}. {o.text}{o.isCorrect && ' (benar)'}
                    </li>
                  ))}
                </ul>
              )}
              {q.type === 'SHORT_ANSWER' && <p className="text-sm" style={{ color: 'var(--ok)' }}>Jawaban diterima: {q.acceptedAnswers.join(' / ')}</p>}
            </li>
          ))}
        </ol>
      )}

      {editing && (
        <Modal title={editing.id ? 'Ubah soal' : 'Tambah soal'} onClose={() => setEditing(null)}>
          <QuestionForm initial={editing.draft} onSave={save} onCancel={() => setEditing(null)} />
        </Modal>
      )}
      {importing && <ImportQuestions examId={exam.id} onClose={() => { setImporting(false); reload(); }} />}
    </div>
  );
}

function ImportQuestions({ examId, onClose }: { examId: string; onClose: () => void }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState<number | null>(null);
  const parsed = text.trim() ? questionsFromCsv(text) : null;

  async function run() {
    if (!parsed || parsed.errors.length) return;
    setBusy(true);
    setError('');
    try {
      let n = 0;
      for (let i = 0; i < parsed.questions.length; i += 100) {
        await api(`/exams/${examId}/questions/import`, { method: 'POST', body: { questions: parsed.questions.slice(i, i + 100) } });
        n += Math.min(100, parsed.questions.length - i);
      }
      setDone(n);
    } catch (err) { setError(err instanceof Error ? err.message : 'Impor gagal.'); }
    setBusy(false);
  }

  if (done !== null) {
    return <Modal title="Impor selesai" onClose={onClose}><p>{done} soal ditambahkan.</p><button className="btn btn-primary mt-4" onClick={onClose}>Selesai</button></Modal>;
  }
  return (
    <Modal title="Impor soal dari CSV" onClose={onClose}>
      <div className="grid gap-4">
        <p className="muted text-sm">Tipe: PG (pilihan ganda), PGK (pilihan ganda kompleks), BS (benar/salah), ISIAN, ESAI. Kolom jawaban: huruf kunci (<span className="mono">B</span> atau <span className="mono">A;C</span>), <span className="mono">Benar</span>/<span className="mono">Salah</span>, atau daftar jawaban dipisah <span className="mono">;</span>.</p>
        <div className="flex flex-wrap gap-2">
          <input type="file" accept=".csv,text/csv,text/plain" aria-label="File CSV" onChange={async (e) => { const f = e.target.files?.[0]; if (f) setText(await f.text()); }} />
          <button type="button" className="btn btn-sm" onClick={() => downloadText('template-soal.csv', QUESTION_TEMPLATE)}>Unduh template</button>
        </div>
        <textarea className="input mono" aria-label="Isi CSV" value={text} onChange={(e) => setText(e.target.value)} />
        {parsed && (
          <div>
            <p className="num">{parsed.questions.length} soal valid{parsed.errors.length > 0 && `, ${parsed.errors.length} baris bermasalah`}.</p>
            {parsed.errors.length > 0 && <ul className="alert alert-bad mt-2 text-sm grid gap-1">{parsed.errors.slice(0, 8).map((m) => <li key={m}>{m}</li>)}</ul>}
          </div>
        )}
        {error && <p role="alert" className="alert alert-bad">{error}</p>}
        <button className="btn btn-primary" disabled={busy || !parsed || parsed.errors.length > 0 || parsed.questions.length === 0} onClick={run}>{busy ? 'Mengimpor…' : 'Impor soal'}</button>
      </div>
    </Modal>
  );
}

// ───────── Settings ─────────
const VISIBILITY: [string, string][] = [
  ['HIDDEN', 'Sembunyikan hasil dari siswa'],
  ['SCORE_ONLY', 'Tampilkan nilai saja'],
  ['SCORE_AND_KKM', 'Tampilkan nilai dan status KKM'],
  ['FULL_DETAIL', 'Tampilkan nilai dan pembahasan'],
];

function SettingsTab({ exam, reload, onSaved, onError }: { exam: Exam; reload: () => void; onSaved: () => void; onError: (e: unknown) => void }) {
  const [f, setF] = useState({
    title: exam.title, description: exam.description ?? '', durationMinutes: exam.durationMinutes,
    startsAt: toLocalInput(exam.startsAt), endsAt: toLocalInput(exam.endsAt), kkm: exam.kkm === null ? '' : String(exam.kkm),
    shuffleQuestions: exam.shuffleQuestions, shuffleOptions: exam.shuffleOptions, offlineMode: exam.offlineMode,
    resultVisibility: exam.resultVisibility, scoreAdjustmentFactor: exam.scoreAdjustmentFactor, scoreMin: exam.scoreMin, scoreMax: exam.scoreMax,
  });
  const [busy, setBusy] = useState(false);
  const [pin, setPin] = useState<string | null>(null);
  const ended = exam.status === 'ENDED';
  const locked = exam._count.sessions > 0;

  async function regenPin() {
    try { setPin((await api<{ supervisorPin: string }>(`/exams/${exam.id}/regenerate-pin`, { method: 'POST' })).supervisorPin); }
    catch (err) { onError(err); }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api(`/exams/${exam.id}`, {
        method: 'PATCH',
        body: {
          ...f,
          description: f.description || null,
          startsAt: fromLocalInput(f.startsAt),
          endsAt: fromLocalInput(f.endsAt),
          kkm: f.kkm === '' ? null : Number(f.kkm),
        },
      });
      onSaved();
      reload();
    } catch (err) { onError(err); }
    setBusy(false);
  }
  const num = (k: 'durationMinutes' | 'scoreAdjustmentFactor' | 'scoreMin' | 'scoreMax') => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: Number(e.target.value) });

  return (
    <form onSubmit={submit} className="glass p-5 grid gap-5 max-w-2xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="label">PIN pengawas</p>
          <p className="num text-2xl font-bold tracking-widest">{pin ?? exam.supervisorPin ?? '–'}</p>
          <p className="muted text-sm">Siswa mengetik PIN ini untuk menyelesaikan ujian atau membuka layar terkunci.</p>
        </div>
        <button type="button" className="btn" disabled={ended} onClick={regenPin}>Buat PIN baru</button>
      </div>
      <fieldset disabled={ended} className="grid gap-5">
        <div>
          <label className="label" htmlFor="t">Judul</label>
          <input id="t" className="input" required minLength={3} maxLength={150} value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="d">Petunjuk untuk siswa (opsional)</label>
          <textarea id="d" className="input" maxLength={2000} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} />
        </div>
        <div className="grid sm:grid-cols-3 gap-3">
          <div>
            <label className="label" htmlFor="dur">Durasi (menit)</label>
            <input id="dur" className="input" type="number" min={1} max={600} required disabled={locked} value={f.durationMinutes} onChange={num('durationMinutes')} />
          </div>
          <div>
            <label className="label" htmlFor="s">Mulai</label>
            <input id="s" className="input" type="datetime-local" value={f.startsAt} onChange={(e) => setF({ ...f, startsAt: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="e">Berakhir</label>
            <input id="e" className="input" type="datetime-local" value={f.endsAt} onChange={(e) => setF({ ...f, endsAt: e.target.value })} />
          </div>
        </div>
        <div className="grid gap-2">
          <label className="check"><input type="checkbox" disabled={locked} checked={f.shuffleQuestions} onChange={(e) => setF({ ...f, shuffleQuestions: e.target.checked })} />Acak urutan soal per siswa</label>
          <label className="check"><input type="checkbox" disabled={locked} checked={f.shuffleOptions} onChange={(e) => setF({ ...f, shuffleOptions: e.target.checked })} />Acak urutan pilihan jawaban</label>
          <label className="check"><input type="checkbox" disabled={locked} checked={f.offlineMode} onChange={(e) => setF({ ...f, offlineMode: e.target.checked })} />Izinkan mode offline (jawaban dikirim saat tersambung lagi)</label>
        </div>
        <div className="grid sm:grid-cols-4 gap-3">
          <div>
            <label className="label" htmlFor="kkm">KKM</label>
            <input id="kkm" className="input" type="number" min={0} max={100} step="0.5" value={f.kkm} onChange={(e) => setF({ ...f, kkm: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="fac">Faktor nilai</label>
            <input id="fac" className="input" type="number" min={0.1} max={3} step="0.05" value={f.scoreAdjustmentFactor} onChange={num('scoreAdjustmentFactor')} />
          </div>
          <div>
            <label className="label" htmlFor="smin">Nilai minimum</label>
            <input id="smin" className="input" type="number" min={0} max={100} value={f.scoreMin} onChange={num('scoreMin')} />
          </div>
          <div>
            <label className="label" htmlFor="smax">Nilai maksimum</label>
            <input id="smax" className="input" type="number" min={0} max={100} value={f.scoreMax} onChange={num('scoreMax')} />
          </div>
        </div>
        <p className="muted text-xs">Faktor, minimum, dan maksimum dipakai untuk menyesuaikan nilai akhir. Penilaian otomatis dijalankan saat siswa mengumpulkan ujian (Phase 3).</p>
        <div>
          <label className="label" htmlFor="vis">Hasil untuk siswa</label>
          <select id="vis" className="input" value={f.resultVisibility} onChange={(e) => setF({ ...f, resultVisibility: e.target.value })}>
            {VISIBILITY.map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <button className="btn btn-primary justify-self-start" disabled={busy || ended}>{busy ? 'Menyimpan…' : 'Simpan pengaturan'}</button>
      </fieldset>
      {ended && <p className="muted text-sm">Ujian yang sudah berakhir tidak bisa diubah.</p>}
    </form>
  );
}

// ───────── Classes ─────────
function ClassesTab({ exam, reload, onError }: { exam: Exam; reload: () => void; onError: (e: unknown) => void }) {
  const [classes, setClasses] = useState<ClassRow[] | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set(exam.assignments.map((a) => a.classId)));
  const [saved, setSaved] = useState(false);
  const ended = exam.status === 'ENDED';

  useEffect(() => { api<{ data: ClassRow[] }>('/classes').then((r) => setClasses(r.data)).catch(onError); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  async function save() {
    try { await api(`/exams/${exam.id}/classes`, { method: 'PUT', body: { classIds: [...picked] } }); setSaved(true); reload(); } catch (err) { onError(err); }
  }
  const toggle = (id: string) => { const n = new Set(picked); n.has(id) ? n.delete(id) : n.add(id); setPicked(n); setSaved(false); };

  if (classes === null) return <p className="muted">Memuat…</p>;
  if (classes.length === 0) return <p className="glass p-5 muted">Belum ada kelas. <Link href="/classes" className="underline">Buat kelas dulu</Link>.</p>;
  return (
    <div className="glass p-5 grid gap-4 max-w-xl">
      <p className="muted text-sm">Semua siswa aktif di kelas yang dipilih menjadi peserta.</p>
      <div className="grid gap-2">
        {classes.map((c) => (
          <label key={c.id} className="check"><input type="checkbox" disabled={ended} checked={picked.has(c.id)} onChange={() => toggle(c.id)} />{c.name} <span className="muted text-sm num">({c._count.students} siswa)</span></label>
        ))}
      </div>
      <div className="flex items-center gap-3">
        <button className="btn btn-primary" onClick={save} disabled={ended}>Simpan kelas peserta</button>
        {saved && <span role="status" className="text-sm" style={{ color: 'var(--ok)' }}>Tersimpan.</span>}
      </div>
    </div>
  );
}

// ───────── Results ─────────
function ResultsTab({ exam, onError }: { exam: Exam; onError: (e: unknown) => void }) {
  const [rows, setRows] = useState<Participant[] | null>(null);
  const load = useCallback(() => { api<{ data: Participant[] }>(`/exams/${exam.id}/participants`).then((r) => setRows(r.data)).catch(onError); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [exam.id]);
  useEffect(load, [load]);

  async function forceSubmit(r: Participant) {
    if (!window.confirm(`Kumpulkan ujian ${r.name} sekarang? Jawaban yang sudah masuk akan dinilai dan tidak bisa diubah.`)) return;
    try { await api(`/exams/${exam.id}/students/${r.studentId}/force-submit`, { method: 'POST' }); load(); }
    catch (err) { onError(err); }
  }

  if (rows === null) return <p className="muted">Memuat…</p>;
  if (rows.length === 0) return <p className="glass p-5 muted">Belum ada peserta. Pilih kelas di tab Kelas dan pastikan kelas itu berisi siswa.</p>;

  const scored = rows.filter((r) => r.finalScore !== null);
  const avg = scored.length ? scored.reduce((a, r) => a + (r.finalScore ?? 0), 0) / scored.length : null;
  const exportCsv = () => downloadText(`hasil-${exam.title.replace(/[^\w-]+/g, '_')}.csv`, toCsv([
    ['Nama', 'Kelas', 'Status', 'Nilai akhir', 'Lulus KKM', 'Peringatan', 'Selesai'],
    ...rows.map((r) => [r.name, r.className ?? '', SESSION_STATUS[r.status]?.[1] ?? r.status, r.finalScore, r.passed === null ? '' : r.passed ? 'Ya' : 'Tidak', r.warningCount, r.finishedAt ?? '']),
  ]));

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="muted num">{rows.length} peserta · {scored.length} sudah bernilai{avg !== null && ` · rata-rata ${avg.toFixed(1)}`}</p>
        <button className="btn" onClick={exportCsv}>Unduh CSV</button>
      </div>
      <section className="glass p-2 scroll-x">
        <table className="table">
          <thead><tr><th>Nama</th><th>Kelas</th><th>Status</th><th>Nilai</th><th>Peringatan</th><th>Selesai</th><th></th></tr></thead>
          <tbody>
            {rows.map((r) => {
              const [cls, text] = SESSION_STATUS[r.status] ?? ['badge-muted', r.status];
              return (
                <tr key={r.studentId}>
                  <td className="font-semibold">{r.name}</td>
                  <td>{r.className ?? '–'}</td>
                  <td><span className={`badge ${cls}`}>{text}</span></td>
                  <td className="num">{r.finalScore === null ? '–' : r.finalScore.toFixed(1)}{r.passed !== null && <span className={`badge ml-2 ${r.passed ? 'badge-ok' : 'badge-bad'}`}>{r.passed ? 'Lulus' : 'Belum lulus'}</span>}</td>
                  <td className="num">{r.warningCount}</td>
                  <td className="muted">{fmtDate(r.finishedAt)}</td>
                  <td>{(r.status === 'IN_PROGRESS' || r.status === 'STOPPED') && <button className="btn" onClick={() => forceSubmit(r)}>Kumpulkan</button>}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>
    </div>
  );
}
