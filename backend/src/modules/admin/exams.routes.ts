import { randomInt } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { audit } from '../../lib/audit';
import { prisma } from '../../lib/db';
import { ah, badRequest, notFound } from '../../lib/errors';
import { finalize } from '../student/student.routes';
import { adminId, idParam, ownExam } from './helpers';

const newPin = () => String(randomInt(0, 1_000_000)).padStart(6, '0');
// No 0/O/1/I/L so a token read aloud or off a whiteboard is hard to mistype.
const TOKEN_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const newToken = () => Array.from({ length: 6 }, () => TOKEN_CHARS[randomInt(0, TOKEN_CHARS.length)]).join('');

export const examsRouter = Router();

// ───────── Exam settings ─────────
const isoDate = z.string().datetime({ offset: true }).nullable().optional();

const HOST_RE = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;
/** "https://Accounts.Google.com/path" and "*.google.com" both become a bare lowercase host. */
const normHost = (s: string) => s.trim().toLowerCase().replace(/^[a-z]+:\/\//, '').replace(/^\*\./, '').replace(/[/:?#].*$/, '');

const examFields = z.object({
  title: z.string().trim().min(3, 'Judul minimal 3 karakter.').max(150),
  description: z.string().trim().max(2000).nullable().optional(),
  durationMinutes: z.number().int().min(1).max(600),
  startsAt: isoDate,
  endsAt: isoDate,
  kkm: z.number().min(0).max(100).nullable().optional(),
  shuffleQuestions: z.boolean().optional(),
  shuffleOptions: z.boolean().optional(),
  resultVisibility: z.enum(['HIDDEN', 'SCORE_ONLY', 'SCORE_AND_KKM', 'FULL_DETAIL']).optional(),
  offlineMode: z.boolean().optional(),
  scoreAdjustmentFactor: z.number().min(0.1).max(3).optional(),
  scoreMin: z.number().min(0).max(100).optional(),
  scoreMax: z.number().min(0).max(100).optional(),
  // Exam browser (mode WEB)
  mode: z.enum(['NATIVE', 'WEB']).optional(),
  webUrl: z.string().trim().max(2000).url('Alamat situs tidak valid.').refine((u) => u.toLowerCase().startsWith('https://'), 'Alamat harus diawali https://').nullable().optional(),
  webAllowedHosts: z
    .array(z.string().trim().max(253))
    .max(30, 'Maksimal 30 situs tambahan.')
    .transform((a) => [...new Set(a.map(normHost).filter(Boolean))])
    .refine((a) => a.every((h) => HOST_RE.test(h)), 'Ada nama situs yang tidak valid. Tulis seperti: accounts.google.com')
    .optional(),
  webLockClipboard: z.boolean().optional(),
});

function checkRanges(v: { startsAt?: string | null; endsAt?: string | null; scoreMin?: number; scoreMax?: number }) {
  if (v.startsAt && v.endsAt && new Date(v.endsAt) <= new Date(v.startsAt)) {
    throw badRequest('INVALID_WINDOW', 'Waktu selesai harus setelah waktu mulai.');
  }
  if (v.scoreMin !== undefined && v.scoreMax !== undefined && v.scoreMin >= v.scoreMax) {
    throw badRequest('INVALID_SCORE_RANGE', 'Nilai minimum harus lebih kecil dari nilai maksimum.');
  }
}

function checkWeb(mode: string | undefined, webUrl: string | null | undefined) {
  if (mode === 'WEB' && !webUrl) throw badRequest('NO_WEB_URL', 'Isi alamat situs ujian (https://...).');
}

const toDate = (v: string | null | undefined) => (v === undefined ? undefined : v === null ? null : new Date(v));

examsRouter.get(
  '/',
  ah(async (req, res) => {
    const rows = await prisma.exam.findMany({
      where: { adminId: adminId(req) },
      orderBy: { createdAt: 'desc' },
      select: {
        id: true, title: true, status: true, mode: true, durationMinutes: true, startsAt: true, endsAt: true, createdAt: true,
        _count: { select: { questions: true, assignments: true, sessions: true } },
      },
    });
    res.json({ data: rows });
  }),
);

examsRouter.post(
  '/',
  ah(async (req, res) => {
    const input = examFields.parse(req.body);
    checkRanges(input);
    checkWeb(input.mode, input.webUrl);
    const exam = await prisma.exam.create({
      data: { ...input, supervisorPin: newPin(), examToken: newToken(), adminId: adminId(req), startsAt: toDate(input.startsAt), endsAt: toDate(input.endsAt) },
    });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'exam.create', targetType: 'Exam', targetId: exam.id, ip: req.ip });
    res.status(201).json(exam);
  }),
);

examsRouter.get(
  '/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const own = await ownExam(id, adminId(req));
    if (!own.supervisorPin || !own.examToken) {
      await prisma.exam.update({ where: { id }, data: { supervisorPin: own.supervisorPin ?? newPin(), examToken: own.examToken ?? newToken() } });
    }
    const exam = await prisma.exam.findUniqueOrThrow({
      where: { id },
      include: {
        questions: { orderBy: { position: 'asc' }, include: { options: { orderBy: { position: 'asc' } } } },
        assignments: { select: { classId: true } },
        _count: { select: { sessions: true } },
      },
    });
    res.json(exam);
  }),
);

// Fields that change what a student experiences are locked once anyone has started.
const LOCKED_AFTER_START = ['durationMinutes', 'shuffleQuestions', 'shuffleOptions', 'offlineMode', 'mode'] as const;

examsRouter.patch(
  '/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const exam = await ownExam(id, adminId(req));
    const input = examFields.partial().parse(req.body);
    if (exam.status === 'ENDED') throw badRequest('EXAM_ENDED', 'Ujian yang sudah berakhir tidak bisa diubah.');

    const started = await prisma.examSession.count({ where: { examId: id, status: { not: 'NOT_STARTED' } } });
    if (started > 0) {
      for (const k of LOCKED_AFTER_START) {
        if (input[k] !== undefined && input[k] !== exam[k]) {
          throw badRequest('EXAM_LOCKED', 'Pengaturan ini terkunci karena sudah ada siswa yang mulai mengerjakan.');
        }
      }
    }
    checkWeb(input.mode ?? exam.mode, input.webUrl === undefined ? exam.webUrl : input.webUrl);
    checkRanges({
      startsAt: input.startsAt === undefined ? exam.startsAt?.toISOString() : input.startsAt,
      endsAt: input.endsAt === undefined ? exam.endsAt?.toISOString() : input.endsAt,
      scoreMin: input.scoreMin ?? exam.scoreMin,
      scoreMax: input.scoreMax ?? exam.scoreMax,
    });
    const updated = await prisma.exam.update({
      where: { id },
      data: { ...input, startsAt: toDate(input.startsAt), endsAt: toDate(input.endsAt) },
    });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'exam.update', targetType: 'Exam', targetId: id, ip: req.ip });
    res.json(updated);
  }),
);

examsRouter.delete(
  '/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownExam(id, adminId(req));
    if ((await prisma.examSession.count({ where: { examId: id } })) > 0) {
      throw badRequest('HAS_SESSIONS', 'Ujian ini sudah punya sesi siswa. Akhiri saja agar hasilnya tersimpan.');
    }
    await prisma.exam.delete({ where: { id } });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'exam.delete', targetType: 'Exam', targetId: id, ip: req.ip });
    res.status(204).end();
  }),
);

// ───────── Status ─────────
type Status = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'ENDED' | 'SUSPENDED';
const TRANSITIONS: Record<Status, Status[]> = {
  DRAFT: ['SCHEDULED', 'ACTIVE'],
  SCHEDULED: ['DRAFT', 'ACTIVE', 'ENDED'],
  ACTIVE: ['SUSPENDED', 'ENDED'],
  SUSPENDED: ['ACTIVE', 'ENDED'],
  ENDED: [],
};

examsRouter.post(
  '/:id/status',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const { status } = z.object({ status: z.enum(['DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED', 'SUSPENDED']) }).parse(req.body);
    const exam = await ownExam(id, adminId(req));
    if (!TRANSITIONS[exam.status].includes(status)) {
      throw badRequest('INVALID_TRANSITION', 'Perubahan status ini tidak diizinkan dari status sekarang.');
    }
    if (exam.status === 'DRAFT') {
      const [questions, classes] = await Promise.all([
        prisma.question.count({ where: { examId: id } }),
        prisma.examAssignment.count({ where: { examId: id } }),
      ]);
      if (exam.mode === 'WEB') {
        if (!exam.webUrl) throw badRequest('NO_WEB_URL', 'Isi alamat situs ujian sebelum ujian dipublikasikan.');
      } else if (questions === 0) throw badRequest('NO_QUESTIONS', 'Tambahkan minimal satu soal sebelum ujian dipublikasikan.');
      if (classes === 0) throw badRequest('NO_CLASSES', 'Pilih minimal satu kelas peserta sebelum ujian dipublikasikan.');
    }
    if (status === 'SCHEDULED' && !exam.startsAt) {
      throw badRequest('NO_START_TIME', 'Isi waktu mulai dulu untuk menjadwalkan ujian.');
    }
    const updated = await prisma.exam.update({ where: { id }, data: { status } });
    await audit({
      actorType: 'ADMIN', actorId: adminId(req), action: 'exam.status', targetType: 'Exam', targetId: id, ip: req.ip,
      metadata: { from: exam.status, to: status },
    });
    res.json(updated);
  }),
);

// ───────── Classes taking the exam ─────────
examsRouter.put(
  '/:id/classes',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const { classIds } = z.object({ classIds: z.array(z.string().uuid()).max(100) }).parse(req.body);
    await ownExam(id, adminId(req));
    const owned = await prisma.class.count({ where: { id: { in: classIds }, adminId: adminId(req) } });
    if (owned !== new Set(classIds).size) throw notFound('Ada kelas yang tidak ditemukan.');
    await prisma.$transaction([
      prisma.examAssignment.deleteMany({ where: { examId: id } }),
      prisma.examAssignment.createMany({ data: [...new Set(classIds)].map((classId) => ({ examId: id, classId })) }),
    ]);
    res.status(204).end();
  }),
);

// ───────── Questions ─────────
const questionSchema = z
  .object({
    type: z.enum(['MULTIPLE_CHOICE', 'MULTIPLE_SELECT', 'TRUE_FALSE', 'SHORT_ANSWER', 'ESSAY']),
    text: z.string().trim().min(1, 'Teks soal wajib diisi.').max(5000),
    weight: z.number().gt(0).max(100).default(1),
    explanation: z.string().trim().max(3000).nullable().optional(),
    category: z.string().trim().max(60).nullable().optional(),
    options: z.array(z.object({ text: z.string().trim().min(1, 'Pilihan tidak boleh kosong.').max(1000), isCorrect: z.boolean() })).max(8).default([]),
    acceptedAnswers: z.array(z.string().trim().min(1).max(200)).max(20).default([]),
  })
  .superRefine((q, ctx) => {
    const fail = (message: string) => ctx.addIssue({ code: z.ZodIssueCode.custom, message });
    const correct = q.options.filter((o) => o.isCorrect).length;
    switch (q.type) {
      case 'MULTIPLE_CHOICE':
        if (q.options.length < 2) fail('Pilihan ganda butuh minimal 2 pilihan.');
        else if (correct !== 1) fail('Pilih tepat satu jawaban benar.');
        break;
      case 'MULTIPLE_SELECT':
        if (q.options.length < 2) fail('Butuh minimal 2 pilihan.');
        else if (correct < 1) fail('Pilih minimal satu jawaban benar.');
        break;
      case 'TRUE_FALSE':
        if (q.options.length !== 2 || correct !== 1) fail('Benar/Salah butuh 2 pilihan dengan satu jawaban benar.');
        break;
      case 'SHORT_ANSWER':
        if (q.acceptedAnswers.length < 1) fail('Isi minimal satu jawaban yang diterima.');
        break;
      case 'ESSAY':
        break;
    }
  });
type QuestionInput = z.infer<typeof questionSchema>;

/** Soal and answers reference each other, so questions freeze once any student has opened the exam. */
async function assertQuestionsEditable(examId: string) {
  if ((await prisma.examSession.count({ where: { examId } })) > 0) {
    throw badRequest('EXAM_LOCKED', 'Soal terkunci karena sudah ada sesi siswa untuk ujian ini.');
  }
}

function questionData(q: QuestionInput) {
  const hasOptions = ['MULTIPLE_CHOICE', 'MULTIPLE_SELECT', 'TRUE_FALSE'].includes(q.type);
  return {
    type: q.type,
    text: q.text,
    weight: q.weight,
    explanation: q.explanation || null,
    category: q.category || null,
    acceptedAnswers: q.type === 'SHORT_ANSWER' ? q.acceptedAnswers : [],
    options: hasOptions ? q.options.map((o, i) => ({ text: o.text, isCorrect: o.isCorrect, position: i })) : [],
  };
}

examsRouter.post(
  '/:id/questions',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownExam(id, adminId(req));
    await assertQuestionsEditable(id);
    const { options, ...data } = questionData(questionSchema.parse(req.body));
    const last = await prisma.question.aggregate({ where: { examId: id }, _max: { position: true } });
    const created = await prisma.question.create({
      data: { ...data, examId: id, position: (last._max.position ?? -1) + 1, options: { create: options } },
      include: { options: { orderBy: { position: 'asc' } } },
    });
    res.status(201).json(created);
  }),
);

examsRouter.post(
  '/:id/questions/import',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const { questions } = z.object({ questions: z.array(questionSchema).min(1).max(200) }).parse(req.body);
    await ownExam(id, adminId(req));
    await assertQuestionsEditable(id);
    const last = await prisma.question.aggregate({ where: { examId: id }, _max: { position: true } });
    let position = (last._max.position ?? -1) + 1;
    await prisma.$transaction(
      questions.map((q) => {
        const { options, ...data } = questionData(q);
        return prisma.question.create({ data: { ...data, examId: id, position: position++, options: { create: options } } });
      }),
    );
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'question.import', targetType: 'Exam', targetId: id, ip: req.ip, metadata: { count: questions.length } });
    res.status(201).json({ created: questions.length });
  }),
);

const questionParams = z.object({ id: z.string().uuid(), questionId: z.string().uuid() });

examsRouter.put(
  '/:id/questions/:questionId',
  ah(async (req, res) => {
    const { id, questionId } = questionParams.parse(req.params);
    await ownExam(id, adminId(req));
    await assertQuestionsEditable(id);
    if (!(await prisma.question.findFirst({ where: { id: questionId, examId: id } }))) throw notFound('Soal tidak ditemukan.');
    const { options, ...data } = questionData(questionSchema.parse(req.body));
    const [, updated] = await prisma.$transaction([
      prisma.questionOption.deleteMany({ where: { questionId } }),
      prisma.question.update({
        where: { id: questionId },
        data: { ...data, options: { create: options } },
        include: { options: { orderBy: { position: 'asc' } } },
      }),
    ]);
    res.json(updated);
  }),
);

examsRouter.delete(
  '/:id/questions/:questionId',
  ah(async (req, res) => {
    const { id, questionId } = questionParams.parse(req.params);
    await ownExam(id, adminId(req));
    await assertQuestionsEditable(id);
    const result = await prisma.question.deleteMany({ where: { id: questionId, examId: id } });
    if (result.count === 0) throw notFound('Soal tidak ditemukan.');
    res.status(204).end();
  }),
);

// ───────── Exam token ─────────
examsRouter.post(
  '/:id/regenerate-token',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownExam(id, adminId(req));
    const exam = await prisma.exam.update({ where: { id }, data: { examToken: newToken() } });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'exam.token.regenerate', targetType: 'Exam', targetId: id, ip: req.ip });
    res.json({ examToken: exam.examToken });
  }),
);

// ───────── Supervisor PIN, individual force-submit ─────────
examsRouter.post(
  '/:id/regenerate-pin',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownExam(id, adminId(req));
    const exam = await prisma.exam.update({ where: { id }, data: { supervisorPin: newPin() } });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'exam.pin.regenerate', targetType: 'Exam', targetId: id, ip: req.ip });
    res.json({ supervisorPin: exam.supervisorPin });
  }),
);

examsRouter.post(
  '/:id/students/:studentId/force-submit',
  ah(async (req, res) => {
    const { id, studentId } = z.object({ id: z.string().uuid(), studentId: z.string().uuid() }).parse(req.params);
    await ownExam(id, adminId(req));
    const session = await prisma.examSession.findFirst({ where: { examId: id, studentId, student: { adminId: adminId(req) } } });
    if (!session) throw notFound('Siswa ini belum memulai ujian.');
    if (!['IN_PROGRESS', 'STOPPED'].includes(session.status)) throw badRequest('NOT_ACTIVE', 'Ujian siswa ini sudah selesai atau belum dimulai.');
    await finalize(session.id, 'FORCE_SUBMITTED');
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'session.force_submit', targetType: 'ExamSession', targetId: session.id, ip: req.ip });
    res.status(204).end();
  }),
);

// Reopens a session that was suspended (forced exit from screen pinning, or stopped by a supervisor).
// The student's refresh tokens were revoked at suspension, so they must sign in again before continuing.
examsRouter.post(
  '/:id/students/:studentId/unsuspend',
  ah(async (req, res) => {
    const { id, studentId } = z.object({ id: z.string().uuid(), studentId: z.string().uuid() }).parse(req.params);
    const { extendMinutes } = z.object({ extendMinutes: z.number().int().min(0).max(240).optional() }).parse(req.body ?? {});
    await ownExam(id, adminId(req));
    const session = await prisma.examSession.findFirst({ where: { examId: id, studentId, student: { adminId: adminId(req) } } });
    if (!session) throw notFound('Siswa ini belum memulai ujian.');
    if (session.status !== 'STOPPED') throw badRequest('NOT_SUSPENDED', 'Sesi siswa ini tidak sedang ditangguhkan.');
    const base = Math.max(session.deadlineAt?.getTime() ?? Date.now(), Date.now());
    await prisma.$transaction([
      prisma.examSession.update({
        where: { id: session.id },
        data: {
          status: 'IN_PROGRESS', suspendReason: null, penaltyUntil: null,
          ...(extendMinutes ? { deadlineAt: new Date(base + extendMinutes * 60_000) } : {}),
        },
      }),
      prisma.student.update({ where: { id: studentId }, data: { failedLogins: 0, lastFailedAt: null, lockedUntil: null } }),
    ]);
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'session.unsuspend', targetType: 'ExamSession', targetId: session.id, metadata: { extendMinutes: extendMinutes ?? 0 }, ip: req.ip });
    res.status(204).end();
  }),
);

// ───────── Participants and results ─────────
// Lists every student in the assigned classes with their session, so "not started" is visible too.
examsRouter.get(
  '/:id/participants',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownExam(id, adminId(req));
    const students = await prisma.student.findMany({
      where: { adminId: adminId(req), class: { assignments: { some: { examId: id } } } },
      orderBy: [{ name: 'asc' }],
      select: {
        id: true, name: true, class: { select: { name: true } },
        sessions: {
          where: { examId: id },
          select: { status: true, suspendReason: true, warningCount: true, startedAt: true, finishedAt: true, result: { select: { finalScore: true, passed: true } } },
        },
      },
    });
    res.json({
      data: students.map((s) => {
        const session = s.sessions[0];
        return {
          studentId: s.id,
          name: s.name,
          className: s.class?.name ?? null,
          status: session?.status ?? 'NOT_STARTED',
          suspended: session?.status === 'STOPPED' && session.suspendReason === 'PIN_EXITED',
          warningCount: session?.warningCount ?? 0,
          startedAt: session?.startedAt ?? null,
          finishedAt: session?.finishedAt ?? null,
          finalScore: session?.result?.finalScore ?? null,
          passed: session?.result?.passed ?? null,
        };
      }),
    });
  }),
);
