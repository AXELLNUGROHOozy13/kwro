export const fmtDate = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short' }) : '–';

/** ISO string -> value for <input type="datetime-local"> in the browser's own time zone. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}
export const fromLocalInput = (v: string): string | null => (v ? new Date(v).toISOString() : null);

export const EXAM_STATUS: Record<string, [string, string]> = {
  DRAFT: ['badge-muted', 'Draf'],
  SCHEDULED: ['badge-warn', 'Terjadwal'],
  ACTIVE: ['badge-ok', 'Berlangsung'],
  SUSPENDED: ['badge-warn', 'Dijeda'],
  ENDED: ['badge-muted', 'Selesai'],
};

export const SESSION_STATUS: Record<string, [string, string]> = {
  NOT_STARTED: ['badge-muted', 'Belum mulai'],
  IN_PROGRESS: ['badge-ok', 'Mengerjakan'],
  STOPPED: ['badge-warn', 'Dihentikan'],
  SUBMITTED: ['badge-ok', 'Selesai'],
  FORCE_SUBMITTED: ['badge-bad', 'Dikumpulkan paksa'],
};

export const QUESTION_TYPES: Record<string, string> = {
  MULTIPLE_CHOICE: 'Pilihan ganda',
  MULTIPLE_SELECT: 'Pilihan ganda kompleks',
  TRUE_FALSE: 'Benar/Salah',
  SHORT_ANSWER: 'Isian singkat',
  ESSAY: 'Esai',
};
