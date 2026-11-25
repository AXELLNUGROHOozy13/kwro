'use client';

import { useState } from 'react';
import { QUESTION_TYPES } from '@/lib/format';

export interface QuestionDraft {
  type: string;
  text: string;
  weight: number;
  explanation: string;
  options: { text: string; isCorrect: boolean }[];
  acceptedAnswers: string;
}

export const emptyDraft = (): QuestionDraft => ({
  type: 'MULTIPLE_CHOICE', text: '', weight: 1, explanation: '',
  options: [{ text: '', isCorrect: true }, { text: '', isCorrect: false }, { text: '', isCorrect: false }, { text: '', isCorrect: false }],
  acceptedAnswers: '',
});

const TF = (correctFirst: boolean) => [{ text: 'Benar', isCorrect: correctFirst }, { text: 'Salah', isCorrect: !correctFirst }];

export function QuestionForm({ initial, onSave, onCancel }: {
  initial: QuestionDraft; onSave: (payload: object) => Promise<void>; onCancel: () => void;
}) {
  const [d, setD] = useState(initial);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const hasOptions = d.type === 'MULTIPLE_CHOICE' || d.type === 'MULTIPLE_SELECT';

  function changeType(type: string) {
    if (type === 'TRUE_FALSE') return setD({ ...d, type, options: TF(true) });
    const needsFresh = d.type === 'TRUE_FALSE' || d.options.length < 2;
    const options = needsFresh ? emptyDraft().options : d.options;
    if (type === 'MULTIPLE_CHOICE') {
      // Single choice keeps only the first marked answer.
      let seen = false;
      return setD({ ...d, type, options: options.map((o) => { const keep = o.isCorrect && !seen; if (o.isCorrect) seen = true; return { ...o, isCorrect: keep }; }) });
    }
    return setD({ ...d, type, options });
  }
  const setOpt = (i: number, patch: Partial<{ text: string; isCorrect: boolean }>) =>
    setD({ ...d, options: d.options.map((o, j) => (j === i ? { ...o, ...patch } : o)) });
  const markCorrect = (i: number) =>
    setD({ ...d, options: d.options.map((o, j) => (d.type === 'MULTIPLE_SELECT' ? (j === i ? { ...o, isCorrect: !o.isCorrect } : o) : { ...o, isCorrect: j === i })) });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onSave({
        type: d.type,
        text: d.text,
        weight: d.weight,
        explanation: d.explanation || null,
        options: d.type === 'TRUE_FALSE' || hasOptions ? d.options : [],
        acceptedAnswers: d.type === 'SHORT_ANSWER' ? d.acceptedAnswers.split('\n').map((s) => s.trim()).filter(Boolean) : [],
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal menyimpan soal.');
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-4">
      <div className="grid grid-cols-1 sm:grid-cols-[1fr_8rem] gap-3">
        <div>
          <label className="label" htmlFor="qtype">Tipe soal</label>
          <select id="qtype" className="input" value={d.type} onChange={(e) => changeType(e.target.value)}>
            {Object.entries(QUESTION_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="qw">Bobot</label>
          <input id="qw" className="input" type="number" step="0.5" min="0.5" max="100" required value={d.weight} onChange={(e) => setD({ ...d, weight: Number(e.target.value) })} />
        </div>
      </div>
      <div>
        <label className="label" htmlFor="qtext">Pertanyaan</label>
        <textarea id="qtext" className="input" required maxLength={5000} value={d.text} onChange={(e) => setD({ ...d, text: e.target.value })} />
      </div>

      {hasOptions && (
        <fieldset className="grid gap-2">
          <legend className="label">Pilihan jawaban ({d.type === 'MULTIPLE_SELECT' ? 'centang semua yang benar' : 'pilih satu yang benar'})</legend>
          {d.options.map((o, i) => (
            <div key={i} className="flex items-center gap-2">
              <input aria-label={`Pilihan ${i + 1} benar`} type={d.type === 'MULTIPLE_SELECT' ? 'checkbox' : 'radio'} name="correct" checked={o.isCorrect} onChange={() => markCorrect(i)} className="w-5 h-5" style={{ accentColor: 'var(--accent-strong)' }} />
              <input aria-label={`Teks pilihan ${i + 1}`} className="input" required maxLength={1000} placeholder={`Pilihan ${String.fromCharCode(65 + i)}`} value={o.text} onChange={(e) => setOpt(i, { text: e.target.value })} />
              {d.options.length > 2 && <button type="button" className="btn btn-sm btn-danger" aria-label={`Hapus pilihan ${i + 1}`} onClick={() => setD({ ...d, options: d.options.filter((_, j) => j !== i) })}>Hapus</button>}
            </div>
          ))}
          {d.options.length < 8 && <button type="button" className="btn btn-sm justify-self-start" onClick={() => setD({ ...d, options: [...d.options, { text: '', isCorrect: false }] })}>Tambah pilihan</button>}
        </fieldset>
      )}

      {d.type === 'TRUE_FALSE' && (
        <fieldset className="flex gap-5">
          <legend className="label">Jawaban yang benar</legend>
          {['Benar', 'Salah'].map((t, i) => (
            <label key={t} className="check"><input type="radio" name="tf" checked={d.options[i]?.isCorrect} onChange={() => setD({ ...d, options: TF(i === 0) })} />{t}</label>
          ))}
        </fieldset>
      )}

      {d.type === 'SHORT_ANSWER' && (
        <div>
          <label className="label" htmlFor="acc">Jawaban yang diterima (satu per baris)</label>
          <textarea id="acc" className="input" required value={d.acceptedAnswers} onChange={(e) => setD({ ...d, acceptedAnswers: e.target.value })} />
          <p className="muted text-xs mt-1">Huruf besar/kecil dan spasi berlebih diabaikan saat dinilai.</p>
        </div>
      )}
      {d.type === 'ESSAY' && <p className="muted text-sm">Esai dinilai manual oleh guru setelah siswa selesai.</p>}

      <div>
        <label className="label" htmlFor="exp">Pembahasan (opsional)</label>
        <textarea id="exp" className="input" style={{ minHeight: '4rem' }} maxLength={3000} value={d.explanation} onChange={(e) => setD({ ...d, explanation: e.target.value })} />
      </div>
      {error && <p role="alert" className="alert alert-bad">{error}</p>}
      <div className="flex gap-2">
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Menyimpan…' : 'Simpan soal'}</button>
        <button type="button" className="btn" onClick={onCancel}>Batal</button>
      </div>
    </form>
  );
}
