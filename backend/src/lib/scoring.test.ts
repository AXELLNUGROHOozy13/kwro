import { describe, expect, it } from 'vitest';
import { gradeAnswers, type GradeQuestion } from './scoring';
import { seededShuffle } from './seeded';

const qs: GradeQuestion[] = [
  { id: 'a', type: 'MULTIPLE_CHOICE', weight: 1, acceptedAnswers: [], options: [{ id: 'a1', isCorrect: true }, { id: 'a2', isCorrect: false }] },
  { id: 'b', type: 'MULTIPLE_SELECT', weight: 2, acceptedAnswers: [], options: [{ id: 'b1', isCorrect: true }, { id: 'b2', isCorrect: true }, { id: 'b3', isCorrect: false }] },
  { id: 'c', type: 'SHORT_ANSWER', weight: 1, acceptedAnswers: ['Jakarta'], options: [] },
  { id: 'd', type: 'ESSAY', weight: 5, acceptedAnswers: [], options: [] },
];
const cfg = { kkm: 70, factor: 1, min: 0, max: 100 };

describe('gradeAnswers', () => {
  it('scores all-correct as 100 and ignores essays', () => {
    const r = gradeAnswers(qs, new Map([['a', { optionIds: ['a1'] }], ['b', { optionIds: ['b2', 'b1'] }], ['c', { text: '  jakarta ' }]]), cfg);
    expect(r).toMatchObject({ rawScore: 100, finalScore: 100, correctCount: 3, incorrectCount: 0, unansweredCount: 0, passed: true });
  });
  it('multi-select needs the exact set', () => {
    const r = gradeAnswers(qs, new Map([['b', { optionIds: ['b1'] }]]), cfg);
    expect(r.incorrectCount).toBe(1);
    expect(r.unansweredCount).toBe(2);
    expect(r.passed).toBe(false);
  });
  it('applies the factor and clamps to the range', () => {
    const r = gradeAnswers(qs, new Map([['a', { optionIds: ['a1'] }], ['b', { optionIds: ['b1', 'b2'] }], ['c', { text: 'Jakarta' }]]), { ...cfg, factor: 1.2 });
    expect(r.rawScore).toBe(100);
    expect(r.finalScore).toBe(100);
  });
});

describe('seededShuffle', () => {
  const items = Array.from({ length: 30 }, (_, i) => i);
  it('is stable for the same seed and differs between seeds', () => {
    expect(seededShuffle(items, 's1')).toEqual(seededShuffle(items, 's1'));
    expect(seededShuffle(items, 's1')).not.toEqual(seededShuffle(items, 's2'));
    expect([...seededShuffle(items, 's1')].sort((x, y) => x - y)).toEqual(items);
  });
});
