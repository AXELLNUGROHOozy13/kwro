// Official scoring. Runs on the server only; the app never decides a score.
export interface GradeQuestion {
  id: string;
  type: string;
  weight: number;
  acceptedAnswers: string[];
  options: { id: string; isCorrect: boolean }[];
}
export interface GradeAnswer {
  optionIds?: string[];
  text?: string;
}
export interface GradeConfig {
  kkm: number | null;
  factor: number;
  min: number;
  max: number;
}

export const normalizeText = (s: string): string => s.normalize('NFKC').trim().toLowerCase().replace(/\s+/g, ' ');
const round2 = (n: number): number => Math.round(n * 100) / 100;

function isAnswered(q: GradeQuestion, a?: GradeAnswer): boolean {
  if (!a) return false;
  if (q.type === 'SHORT_ANSWER') return !!a.text && normalizeText(a.text).length > 0;
  return !!a.optionIds && a.optionIds.length > 0;
}

function isCorrect(q: GradeQuestion, a: GradeAnswer): boolean {
  if (q.type === 'SHORT_ANSWER') {
    const given = normalizeText(a.text ?? '');
    return q.acceptedAnswers.some((x) => normalizeText(x) === given);
  }
  const correct = new Set(q.options.filter((o) => o.isCorrect).map((o) => o.id));
  const given = new Set(a.optionIds ?? []);
  return correct.size === given.size && [...correct].every((id) => given.has(id)); // multi-select needs the exact set
}

/** Essays are graded by hand, so they are left out of the automatic score and counts. */
export function gradeAnswers(questions: GradeQuestion[], answers: Map<string, GradeAnswer>, cfg: GradeConfig) {
  let earned = 0;
  let total = 0;
  let correct = 0;
  let incorrect = 0;
  let unanswered = 0;
  for (const q of questions) {
    if (q.type === 'ESSAY') continue;
    total += q.weight;
    const a = answers.get(q.id);
    if (!isAnswered(q, a)) {
      unanswered++;
    } else if (isCorrect(q, a!)) {
      earned += q.weight;
      correct++;
    } else {
      incorrect++;
    }
  }
  const raw = total > 0 ? (earned / total) * 100 : 0;
  const final = Math.min(cfg.max, Math.max(cfg.min, round2(raw * cfg.factor)));
  return {
    rawScore: round2(raw),
    finalScore: final,
    correctCount: correct,
    incorrectCount: incorrect,
    unansweredCount: unanswered,
    passed: cfg.kkm == null ? null : final >= cfg.kkm,
  };
}
