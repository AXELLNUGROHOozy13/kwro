'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useApiGuard, useMe } from '@/components/Shell';

interface Stats {
  classes: number;
  students: number;
  exams: Record<string, number>;
  inProgress: number;
  violations24h: number;
}

export default function Dashboard() {
  const me = useMe();
  const [s, setS] = useState<Stats | null>(null);
  const [error, setError] = useState('');
  const guard = useApiGuard(setError);

  useEffect(() => {
    api<Stats>('/stats').then(setS).catch(guard);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const totalExams = s ? Object.values(s.exams).reduce((a, b) => a + b, 0) : 0;
  const steps = s
    ? [
        { done: s.classes > 0, text: 'Buat kelas', href: '/classes' },
        { done: s.students > 0, text: 'Tambah siswa (satu per satu atau impor CSV)', href: '/students' },
        { done: totalExams > 0, text: 'Buat ujian dan isi soal', href: '/exams' },
        { done: s.exams.ACTIVE > 0 || s.exams.ENDED > 0, text: 'Pilih kelas peserta, lalu publikasikan', href: '/exams' },
      ]
    : [];
  const nextStep = steps.findIndex((x) => !x.done);

  const cards: [string, number][] = s
    ? [
        ['Ujian berlangsung', s.exams.ACTIVE],
        ['Siswa sedang mengerjakan', s.inProgress],
        ['Pelanggaran 24 jam terakhir', s.violations24h],
        ['Siswa', s.students],
        ['Kelas', s.classes],
        ['Ujian draf', s.exams.DRAFT],
      ]
    : [];

  return (
    <div className="grid gap-5">
      <header>
        <h1 className="h-page">Halo, {me?.name.split(' ')[0]}</h1>
        <p className="muted">{me?.schoolName}</p>
      </header>
      {error && <p role="alert" className="alert alert-bad">{error}</p>}

      <section className="grid grid-cols-2 lg:grid-cols-3 gap-3" aria-label="Ringkasan">
        {cards.map(([label, value]) => (
          <div key={label} className="glass p-4">
            <p className="muted text-sm">{label}</p>
            <p className="stat-value num">{value}</p>
          </div>
        ))}
      </section>

      {s && nextStep !== -1 && (
        <section className="glass p-5 grid gap-3">
          <h2 className="h-sec">Langkah menuju ujian pertama</h2>
          <ol className="grid gap-2">
            {steps.map((st, i) => (
              <li key={st.text} className="flex items-center gap-3">
                <span aria-hidden className={`badge ${st.done ? 'badge-ok' : i === nextStep ? 'badge-warn' : 'badge-muted'}`}>{st.done ? '✓' : i + 1}</span>
                {st.done ? <span className="muted line-through">{st.text}</span> : <Link href={st.href} className={i === nextStep ? 'font-semibold underline' : 'muted'}>{st.text}</Link>}
              </li>
            ))}
          </ol>
        </section>
      )}

      {s && s.exams.ACTIVE > 0 && (
        <section className="glass p-5">
          <p>Ada {s.exams.ACTIVE} ujian yang sedang berlangsung. Pemantauan langsung tersedia di Phase 5.</p>
        </section>
      )}
    </div>
  );
}
