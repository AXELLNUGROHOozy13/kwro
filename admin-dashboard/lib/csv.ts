/** Minimal CSV reader: quoted fields, escaped quotes, "," or ";" delimiter (Excel in Indonesia exports ";"). */
export function parseCsv(text: string): string[][] {
  const src = text.replace(/^\uFEFF/, '');
  const firstLine = src.split(/\r?\n/, 1)[0] ?? '';
  const delim = (firstLine.match(/;/g)?.length ?? 0) > (firstLine.match(/,/g)?.length ?? 0) ? ';' : ',';
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < src.length; i++) {
    const c = src[i];
    if (quoted) {
      if (c === '"' && src[i + 1] === '"') { cell += '"'; i++; }
      else if (c === '"') quoted = false;
      else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === delim) { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && src[i + 1] === '\n') i++;
      row.push(cell); cell = '';
      if (row.some((v) => v.trim() !== '')) rows.push(row);
      row = [];
    } else cell += c;
  }
  row.push(cell);
  if (row.some((v) => v.trim() !== '')) rows.push(row);
  return rows.map((r) => r.map((v) => v.trim()));
}

/** Builds a CSV Excel opens correctly (BOM) and neutralises cells that would run as formulas. */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  const esc = (v: string | number | null | undefined) => {
    let s = v === null || v === undefined ? '' : String(v);
    if (/^[=+\-@\t\r]/.test(s) && Number.isNaN(Number(s))) s = `'${s}`;
    return /[",;\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return '\uFEFF' + rows.map((r) => r.map(esc).join(',')).join('\r\n');
}

export function downloadText(filename: string, content: string, mime = 'text/csv;charset=utf-8'): void {
  const url = URL.createObjectURL(new Blob([content], { type: mime }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ───────── Question import ─────────
export interface QuestionPayload {
  type: 'MULTIPLE_CHOICE' | 'MULTIPLE_SELECT' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY';
  text: string;
  weight: number;
  options: { text: string; isCorrect: boolean }[];
  acceptedAnswers: string[];
}

export const QUESTION_TEMPLATE = toCsv([
  ['tipe', 'soal', 'bobot', 'a', 'b', 'c', 'd', 'e', 'jawaban'],
  ['PG', 'Ibu kota Indonesia adalah…', 1, 'Bandung', 'Jakarta', 'Surabaya', 'Medan', '', 'B'],
  ['PGK', 'Pilih bilangan prima.', 2, '2', '4', '5', '9', '', 'A;C'],
  ['BS', 'Matahari terbit dari timur.', 1, '', '', '', '', '', 'Benar'],
  ['ISIAN', 'Hasil dari 7 x 8 adalah…', 1, '', '', '', '', '', '56;lima puluh enam'],
  ['ESAI', 'Jelaskan proses fotosintesis.', 5, '', '', '', '', '', ''],
]);

const TYPE_MAP: Record<string, QuestionPayload['type']> = {
  PG: 'MULTIPLE_CHOICE', PGK: 'MULTIPLE_SELECT', BS: 'TRUE_FALSE', ISIAN: 'SHORT_ANSWER', ESAI: 'ESSAY',
};

export function questionsFromCsv(text: string): { questions: QuestionPayload[]; errors: string[] } {
  const rows = parseCsv(text);
  const errors: string[] = [];
  const questions: QuestionPayload[] = [];
  if (rows.length < 2) return { questions, errors: ['File kosong atau hanya berisi judul kolom.'] };

  const head = rows[0].map((h) => h.toLowerCase());
  const col = (name: string) => head.indexOf(name);
  for (const need of ['tipe', 'soal', 'jawaban']) {
    if (col(need) < 0) return { questions, errors: [`Kolom "${need}" tidak ditemukan. Pakai template yang disediakan.`] };
  }
  const letters = ['a', 'b', 'c', 'd', 'e'];

  rows.slice(1).forEach((r, idx) => {
    const line = idx + 2;
    const get = (name: string) => (col(name) >= 0 ? (r[col(name)] ?? '') : '');
    const type = TYPE_MAP[get('tipe').toUpperCase()];
    const text = get('soal');
    if (!type) return void errors.push(`Baris ${line}: tipe harus PG, PGK, BS, ISIAN, atau ESAI.`);
    if (!text) return void errors.push(`Baris ${line}: teks soal kosong.`);
    const weightRaw = get('bobot');
    const weight = weightRaw === '' ? 1 : Number(weightRaw.replace(',', '.'));
    if (!Number.isFinite(weight) || weight <= 0) return void errors.push(`Baris ${line}: bobot harus angka lebih dari 0.`);

    const answer = get('jawaban');
    const q: QuestionPayload = { type, text, weight, options: [], acceptedAnswers: [] };

    if (type === 'MULTIPLE_CHOICE' || type === 'MULTIPLE_SELECT') {
      const present = letters.map((l) => ({ l: l.toUpperCase(), text: get(l) })).filter((o) => o.text !== '');
      const keys = answer.split(/[;,\s]+/).map((k) => k.toUpperCase()).filter(Boolean);
      if (present.length < 2) return void errors.push(`Baris ${line}: isi minimal 2 pilihan.`);
      if (keys.some((k) => !present.some((p) => p.l === k))) return void errors.push(`Baris ${line}: kunci jawaban merujuk pilihan yang kosong.`);
      if (type === 'MULTIPLE_CHOICE' && keys.length !== 1) return void errors.push(`Baris ${line}: PG butuh tepat satu kunci jawaban.`);
      if (keys.length < 1) return void errors.push(`Baris ${line}: kunci jawaban kosong.`);
      q.options = present.map((p) => ({ text: p.text, isCorrect: keys.includes(p.l) }));
    } else if (type === 'TRUE_FALSE') {
      const a = answer.toLowerCase();
      if (!['benar', 'salah'].includes(a)) return void errors.push(`Baris ${line}: jawaban BS harus "Benar" atau "Salah".`);
      q.options = [{ text: 'Benar', isCorrect: a === 'benar' }, { text: 'Salah', isCorrect: a === 'salah' }];
    } else if (type === 'SHORT_ANSWER') {
      q.acceptedAnswers = answer.split(';').map((v) => v.trim()).filter(Boolean);
      if (q.acceptedAnswers.length < 1) return void errors.push(`Baris ${line}: isi minimal satu jawaban yang diterima.`);
    }
    questions.push(q);
  });
  return { questions, errors };
}

// ───────── Student import ─────────
export function studentsFromCsv(text: string): { students: { name: string; className?: string }[]; errors: string[] } {
  const rows = parseCsv(text);
  const errors: string[] = [];
  const students: { name: string; className?: string }[] = [];
  const hasHeader = rows[0]?.[0]?.toLowerCase() === 'nama';
  const nameCol = hasHeader ? rows[0].findIndex((h) => h.toLowerCase() === 'nama') : 0;
  const classCol = hasHeader ? rows[0].findIndex((h) => h.toLowerCase() === 'kelas') : 1;
  rows.slice(hasHeader ? 1 : 0).forEach((r, idx) => {
    const line = idx + (hasHeader ? 2 : 1);
    const name = (r[nameCol] ?? '').trim();
    if (name.length < 2) return void errors.push(`Baris ${line}: nama minimal 2 karakter.`);
    const className = classCol >= 0 ? (r[classCol] ?? '').trim() : '';
    students.push({ name, className: className || undefined });
  });
  return { students, errors };
}
