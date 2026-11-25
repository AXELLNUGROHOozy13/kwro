import { randomBytes } from 'node:crypto';
import { Router, type Request } from 'express';
import { z } from 'zod';
import { prisma } from '../../lib/db';
import { ah, AppError, badRequest, notFound } from '../../lib/errors';
import { hashPassword, verifyPassword } from '../../lib/password';
import { gradeAnswers, type GradeAnswer } from '../../lib/scoring';
import { seededShuffle } from '../../lib/seeded';
import { authenticate } from '../../middleware/auth';

export const studentRouter = Router();
studentRouter.use(authenticate('STUDENT'));

const studentId = (req: Request): string => req.auth!.id;
const idParam = z.object({ id: z.string().uuid() });
// Warning n => penalty minutes. Mirrors the app's local prediction; the server value is the official one.
const PENALTY_MINUTES = [5, 10, 20];
const ANSWER_GRACE_MS = 15_000;
const OFFLINE_SYNC_WINDOW_MS = 30 * 60_000;
const DONE = ['SUBMITTED', 'FORCE_SUBMITTED'];

async function loadStudent(req: Request) {
  return prisma.student.findUniqueOrThrow({ where: { id: studentId(req) }, include: { class: { select: { name: true } } } });
}

/** Exam endpoints stay closed until the student has replaced the initial password. */
async function requirePasswordChanged(req: Request) {
  const s = await loadStudent(req);
  if (s.mustChangePassword) throw new AppError(403, 'PASSWORD_CHANGE_REQUIRED', 'Ganti kata sandi dulu sebelum ujian.');
  return s;
}

studentRouter.get(
  '/me',
  ah(async (req, res) => {
    const s = await loadStudent(req);
    res.json({ id: s.id, name: s.name, className: s.class?.name ?? null, mustChangePassword: s.mustChangePassword });
  }),
);

studentRouter.post(
  '/change-password',
  ah(async (req, res) => {
    const body = z.object({ currentPassword: z.string().min(1).max(256), newPassword: z.string().min(8, 'Minimal 8 karakter.').max(100) }).parse(req.body);
    const s = await loadStudent(req);
    if (!(await verifyPassword(s.passwordHash, body.currentPassword))) throw badRequest('WRONG_PASSWORD', 'Kata sandi lama salah.');
    if (body.newPassword === s.loginCode) throw badRequest('WEAK_PASSWORD', 'Kata sandi baru tidak boleh sama dengan kode login.');
    await prisma.student.update({ where: { id: s.id }, data: { passwordHash: await hashPassword(body.newPassword), mustChangePassword: false } });
    res.status(204).end();
  }),
);

// ───────── Exam list ─────────
function availability(exam: { status: string; startsAt: Date | null; endsAt: Date | null }, sessionStatus: string | undefined, now: Date) {
  if (sessionStatus && DONE.includes(sessionStatus)) return { canStart: false, reason: 'Sudah dikumpulkan.' };
  if (sessionStatus === 'STOPPED') return { canStart: false, reason: 'Dihentikan pengawas. Hubungi pengawas.' };
  if (sessionStatus === 'IN_PROGRESS') return { canStart: true, reason: null };
  const open = exam.status === 'ACTIVE' || (exam.status === 'SCHEDULED' && !!exam.startsAt && exam.startsAt <= now);
  if (!open || (exam.startsAt && now < exam.startsAt)) return { canStart: false, reason: 'Belum dimulai.' };
  if (exam.endsAt && now >= exam.endsAt) return { canStart: false, reason: 'Sudah berakhir.' };
  return { canStart: true, reason: null };
}

studentRouter.get(
  '/exams',
  ah(async (req, res) => {
    const s = await requirePasswordChanged(req);
    const now = new Date();
    const exams = s.classId
      ? await prisma.exam.findMany({
          where: { adminId: s.adminId, status: { in: ['SCHEDULED', 'ACTIVE'] }, assignments: { some: { classId: s.classId } } },
          orderBy: [{ startsAt: 'asc' }, { createdAt: 'desc' }],
          include: { sessions: { where: { studentId: s.id }, select: { id: true, status: true } } },
        })
      : [];
    res.json({
      serverTime: now.toISOString(),
      data: exams.map((e) => {
        const sess = e.sessions[0];
        return {
          id: e.id, title: e.title, description: e.description, durationMinutes: e.durationMinutes,
          startsAt: e.startsAt?.toISOString() ?? null, endsAt: e.endsAt?.toISOString() ?? null, status: e.status,
          sessionId: sess?.id ?? null, sessionStatus: sess?.status ?? null,
          ...availability(e, sess?.status, now),
        };
      }),
    });
  }),
);

// ───────── Session helpers ─────────
async function latestAnswers(sessionId: string): Promise<Map<string, GradeAnswer>> {
  const rows = await prisma.studentAnswer.findMany({ where: { sessionId }, orderBy: [{ answeredAt: 'desc' }, { receivedAt: 'desc' }] });
  const map = new Map<string, GradeAnswer>();
  for (const r of rows) if (!map.has(r.questionId)) map.set(r.questionId, r.answer as unknown as GradeAnswer);
  return map;
}

async function ownSession(req: Request, id: string) {
  await requirePasswordChanged(req);
  const session = await prisma.examSession.findFirst({ where: { id, studentId: studentId(req) }, include: { exam: true } });
  if (!session) throw notFound('Sesi ujian tidak ditemukan.');
  return session;
}

async function finalize(sessionId: string, to: 'SUBMITTED' | 'FORCE_SUBMITTED') {
  const claimed = await prisma.examSession.updateMany({
    where: { id: sessionId, status: { in: ['IN_PROGRESS', 'STOPPED'] } },
    data: { status: to, finishedAt: new Date() },
  });
  if (claimed.count !== 1) return; // someone else already finalized it (idempotent)
  const session = await prisma.examSession.findUniqueOrThrow({
    where: { id: sessionId },
    include: { exam: { include: { questions: { include: { options: true } } } } },
  });
  const exam = session.exam;
  const g = gradeAnswers(
    exam.questions.map((q) => ({ id: q.id, type: q.type, weight: q.weight, acceptedAnswers: q.acceptedAnswers, options: q.options.map((o) => ({ id: o.id, isCorrect: o.isCorrect })) })),
    await latestAnswers(sessionId),
    { kkm: exam.kkm, factor: exam.scoreAdjustmentFactor, min: exam.scoreMin, max: exam.scoreMax },
  );
  await prisma.examResult.upsert({ where: { sessionId }, create: { sessionId, ...g }, update: g });
}

/** Time is server-authoritative: an expired session is closed here even if the phone never reports it. */
async function closeIfExpired<T extends { id: string; status: string; deadlineAt: Date | null; exam: { offlineMode: boolean } }>(s: T): Promise<T> {
  if (s.status === 'IN_PROGRESS' && s.deadlineAt) {
    const limit = s.deadlineAt.getTime() + (s.exam.offlineMode ? OFFLINE_SYNC_WINDOW_MS : ANSWER_GRACE_MS);
    if (Date.now() > limit) {
      await finalize(s.id, 'SUBMITTED');
      return { ...s, status: 'SUBMITTED' };
    }
  }
  return s;
}

async function payload(sessionId: string) {
  const s = await prisma.examSession.findUniqueOrThrow({
    where: { id: sessionId },
    include: { exam: { include: { questions: { include: { options: { orderBy: { position: 'asc' } } }, orderBy: { position: 'asc' } } } } },
  });
  const ordered = s.exam.shuffleQuestions ? seededShuffle(s.exam.questions, `${s.seed}:q`) : s.exam.questions;
  const questions = ordered.map((q) => {
    const choice = q.type === 'MULTIPLE_CHOICE' || q.type === 'MULTIPLE_SELECT';
    const opts = choice && s.exam.shuffleOptions ? seededShuffle(q.options, `${s.seed}:o:${q.id}`) : q.options;
    // Correct answers, explanations and accepted answers never leave the server.
    return { id: q.id, type: q.type, text: q.text, imageUrl: q.imageUrl, options: opts.map((o) => ({ id: o.id, text: o.text })) };
  });
  const answers = [...(await latestAnswers(sessionId)).entries()].map(([questionId, a]) => ({ questionId, optionIds: a.optionIds ?? null, text: a.text ?? null }));
  return {
    sessionId: s.id, examId: s.examId, title: s.exam.title, status: s.status,
    deadlineAt: s.deadlineAt!.toISOString(), serverTime: new Date().toISOString(), offlineMode: s.exam.offlineMode,
    warningCount: s.warningCount, penaltyUntil: s.penaltyUntil && s.penaltyUntil > new Date() ? s.penaltyUntil.toISOString() : null,
    questions, answers,
  };
}

// ───────── Start / resume ─────────
studentRouter.post(
  '/exams/:id/start',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const st = await requirePasswordChanged(req);
    const exam = await prisma.exam.findFirst({
      where: { id, adminId: st.adminId, assignments: { some: { classId: st.classId ?? '' } } },
    });
    if (!exam) throw notFound('Ujian tidak ditemukan.');
    const now = new Date();
    let session = await prisma.examSession.findUnique({ where: { examId_studentId: { examId: id, studentId: st.id } } });
    if (session && DONE.includes(session.status)) throw badRequest('ALREADY_SUBMITTED', 'Ujian ini sudah dikumpulkan.');
    if (session?.status === 'STOPPED') throw badRequest('SESSION_STOPPED', 'Ujian dihentikan pengawas. Hubungi pengawas.');

    if (!session || session.status === 'NOT_STARTED') {
      const a = availability(exam, undefined, now);
      if (!a.canStart) throw badRequest('NOT_AVAILABLE', a.reason ?? 'Ujian belum bisa dimulai.');
      let deadline = new Date(now.getTime() + exam.durationMinutes * 60_000);
      if (exam.endsAt && exam.endsAt < deadline) deadline = exam.endsAt; // never run past the exam window
      const data = { status: 'IN_PROGRESS' as const, startedAt: now, deadlineAt: deadline };
      session = await prisma.examSession.upsert({
        where: { examId_studentId: { examId: id, studentId: st.id } },
        create: { examId: id, studentId: st.id, seed: randomBytes(16).toString('hex'), ...data },
        update: session ? data : {},
      });
    }
    await closeIfExpired({ ...session, exam });
    res.json(await payload(session.id));
  }),
);

studentRouter.get(
  '/sessions/:id',
  ah(async (req, res) => {
    const s = await closeIfExpired(await ownSession(req, idParam.parse(req.params).id));
    res.json(await payload(s.id));
  }),
);

// ───────── Answers (idempotent, append-only) ─────────
const answersSchema = z.object({
  answers: z.array(z.object({
    clientEventId: z.string().min(8).max(64),
    questionId: z.string().uuid(),
    optionIds: z.array(z.string().uuid()).max(8).nullish(),
    text: z.string().max(5000).nullish(),
    answeredAt: z.string().datetime({ offset: true }),
  })).min(1).max(100),
});

studentRouter.put(
  '/sessions/:id/answers',
  ah(async (req, res) => {
    const session = await closeIfExpired(await ownSession(req, idParam.parse(req.params).id));
    if (session.status !== 'IN_PROGRESS') throw new AppError(409, 'SESSION_NOT_ACTIVE', 'Sesi ujian sudah tidak aktif.');
    const { answers } = answersSchema.parse(req.body);
    const now = Date.now();
    const deadline = session.deadlineAt!.getTime();
    // Online exams: nothing after the deadline. Offline exams: judge by when the answer was given, not when it arrived.
    if (!session.exam.offlineMode && now > deadline + ANSWER_GRACE_MS) throw badRequest('TIME_UP', 'Waktu ujian sudah habis.');

    const questions = await prisma.question.findMany({ where: { examId: session.examId }, select: { id: true, type: true, options: { select: { id: true } } } });
    const byId = new Map(questions.map((q) => [q.id, q]));
    const rows = [];
    for (const a of answers) {
      const q = byId.get(a.questionId);
      if (!q) continue;
      const at = Math.min(Math.max(new Date(a.answeredAt).getTime(), session.startedAt?.getTime() ?? 0), now + 60_000);
      if (session.exam.offlineMode && at > deadline + ANSWER_GRACE_MS) continue;
      const text = q.type === 'SHORT_ANSWER' || q.type === 'ESSAY';
      const valid = new Set(q.options.map((o) => o.id));
      const optionIds = text ? undefined : (a.optionIds ?? []).filter((x) => valid.has(x));
      rows.push({
        sessionId: session.id, questionId: q.id, clientEventId: a.clientEventId, answeredAt: new Date(at),
        answer: text ? { text: a.text ?? '' } : { optionIds },
      });
    }
    if (rows.length) await prisma.studentAnswer.createMany({ data: rows, skipDuplicates: true });
    res.status(204).end();
  }),
);

// ───────── Violations ─────────
const violationsSchema = z.object({
  events: z.array(z.object({
    clientEventId: z.string().min(8).max(64),
    type: z.enum(['LEFT_APP', 'FOCUS_LOST', 'MULTI_WINDOW', 'OVERLAY_DETECTED', 'PINNING_EXITED']),
    occurredAt: z.string().datetime({ offset: true }),
    durationMs: z.number().int().min(0).max(86_400_000).nullish(),
  })).min(1).max(20),
});

studentRouter.post(
  '/sessions/:id/violations',
  ah(async (req, res) => {
    const session = await closeIfExpired(await ownSession(req, idParam.parse(req.params).id));
    const { events } = violationsSchema.parse(req.body);
    let warningCount = session.warningCount;
    let penaltyUntil = session.penaltyUntil;
    let lastMinutes = 0;

    if (session.status === 'IN_PROGRESS') {
      const known = new Set((await prisma.violationLog.findMany({ where: { clientEventId: { in: events.map((e) => e.clientEventId) } }, select: { clientEventId: true } })).map((v) => v.clientEventId));
      let lastAt = (await prisma.violationLog.findFirst({ where: { sessionId: session.id }, orderBy: { occurredAt: 'desc' } }))?.occurredAt.getTime() ?? 0;
      for (const e of [...events].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))) {
        const at = new Date(e.occurredAt).getTime();
        // Ignore repeats, events outside the session, and the same leave reported twice (FOCUS_LOST + LEFT_APP).
        if (known.has(e.clientEventId) || at < (session.startedAt?.getTime() ?? 0) || at > Date.now() + 60_000 || at - lastAt < 2000) continue;
        const minutes = PENALTY_MINUTES[Math.min(warningCount, PENALTY_MINUTES.length - 1)];
        warningCount += 1;
        lastAt = at;
        lastMinutes = minutes;
        const until = new Date(at + minutes * 60_000); // measured from the violation, so late uploads do not lock the student afterwards
        if (!penaltyUntil || until > penaltyUntil) penaltyUntil = until;
        await prisma.violationLog.create({
          data: { sessionId: session.id, type: e.type, occurredAt: new Date(at), durationMs: e.durationMs ?? null, clientEventId: e.clientEventId, penaltyMinutes: minutes, status: 'CONFIRMED' },
        });
      }
      if (lastMinutes > 0) await prisma.examSession.update({ where: { id: session.id }, data: { warningCount, penaltyUntil } });
    }
    res.json({
      warningCount, penaltyMinutes: lastMinutes, serverTime: new Date().toISOString(),
      penaltyUntil: penaltyUntil && penaltyUntil > new Date() ? penaltyUntil.toISOString() : null,
    });
  }),
);

// ───────── Heartbeat / submit ─────────
studentRouter.post(
  '/sessions/:id/heartbeat',
  ah(async (req, res) => {
    const s = await closeIfExpired(await ownSession(req, idParam.parse(req.params).id));
    res.json({
      status: s.status, serverTime: new Date().toISOString(), deadlineAt: s.deadlineAt!.toISOString(), warningCount: s.warningCount,
      penaltyUntil: s.penaltyUntil && s.penaltyUntil > new Date() ? s.penaltyUntil.toISOString() : null,
    });
  }),
);

studentRouter.post(
  '/sessions/:id/submit',
  ah(async (req, res) => {
    const session = await ownSession(req, idParam.parse(req.params).id);
    if (session.status === 'IN_PROGRESS') await finalize(session.id, 'SUBMITTED');
    const fresh = await prisma.examSession.findUniqueOrThrow({ where: { id: session.id }, include: { result: true, exam: true } });
    if (!DONE.includes(fresh.status) || !fresh.result) {
      throw new AppError(409, 'NOT_SUBMITTED', 'Ujian belum selesai (dihentikan pengawas).');
    }
    const { result, exam } = fresh;
    const v = exam.resultVisibility;
    if (v === 'HIDDEN') return res.json({ visible: false, message: 'Jawabanmu sudah terkirim. Hasil akan diumumkan oleh pengawas.' });
    res.json({
      visible: true,
      finalScore: result.finalScore,
      ...(v === 'SCORE_AND_KKM' || v === 'FULL_DETAIL' ? { passed: result.passed, kkm: exam.kkm } : {}),
      ...(v === 'FULL_DETAIL' ? { correct: result.correctCount, incorrect: result.incorrectCount, unanswered: result.unansweredCount } : {}),
    });
  }),
);
