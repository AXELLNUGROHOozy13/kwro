import { randomInt } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { audit } from '../../lib/audit';
import { prisma } from '../../lib/db';
import { ah, badRequest } from '../../lib/errors';
import { hashPassword, verifyPassword } from '../../lib/password';
import { authenticate } from '../../middleware/auth';
import { examsRouter } from './exams.routes';
import { adminId, idParam, ownClass, ownStudent, pageSchema } from './helpers';

export const adminRouter = Router();
adminRouter.use(authenticate('ADMIN'));

// ───────── Profile ─────────
adminRouter.get(
  '/me',
  ah(async (req, res) => {
    const me = await prisma.administrator.findUnique({
      where: { id: adminId(req) },
      select: { id: true, email: true, name: true, schoolName: true },
    });
    res.json(me);
  }),
);

const passwordSchema = z.object({
  currentPassword: z.string().min(1).max(256),
  newPassword: z.string().min(12, 'Gunakan minimal 12 karakter.').max(256),
});
adminRouter.post(
  '/me/password',
  ah(async (req, res) => {
    const input = passwordSchema.parse(req.body);
    const me = await prisma.administrator.findUniqueOrThrow({ where: { id: adminId(req) } });
    if (!(await verifyPassword(me.passwordHash, input.currentPassword))) {
      throw badRequest('WRONG_PASSWORD', 'Kata sandi saat ini salah.');
    }
    const passwordHash = await hashPassword(input.newPassword);
    await prisma.$transaction([
      prisma.administrator.update({ where: { id: me.id }, data: { passwordHash } }),
      // Every device must sign in again with the new password.
      prisma.refreshToken.updateMany({ where: { adminId: me.id, revokedAt: null }, data: { revokedAt: new Date() } }),
    ]);
    await audit({ actorType: 'ADMIN', actorId: me.id, action: 'admin.password_change', ip: req.ip });
    res.status(204).end();
  }),
);

// ───────── Overview ─────────
adminRouter.get(
  '/stats',
  ah(async (req, res) => {
    const id = adminId(req);
    const since = new Date(Date.now() - 24 * 3_600_000);
    const [classes, students, examGroups, inProgress, violations24h] = await Promise.all([
      prisma.class.count({ where: { adminId: id } }),
      prisma.student.count({ where: { adminId: id } }),
      prisma.exam.groupBy({ by: ['status'], where: { adminId: id }, _count: { _all: true } }),
      prisma.examSession.count({ where: { exam: { adminId: id }, status: 'IN_PROGRESS' } }),
      prisma.violationLog.count({
        where: { session: { exam: { adminId: id } }, status: 'CONFIRMED', occurredAt: { gte: since } },
      }),
    ]);
    const exams: Record<string, number> = { DRAFT: 0, SCHEDULED: 0, ACTIVE: 0, ENDED: 0, SUSPENDED: 0 };
    for (const g of examGroups) exams[g.status] = g._count._all;
    res.json({ classes, students, exams, inProgress, violations24h });
  }),
);

// ───────── Classes ─────────
const classSchema = z.object({ name: z.string().trim().min(1, 'Nama kelas wajib diisi.').max(60) });

adminRouter.get(
  '/classes',
  ah(async (req, res) => {
    const rows = await prisma.class.findMany({
      where: { adminId: adminId(req) },
      orderBy: { name: 'asc' },
      select: { id: true, name: true, createdAt: true, _count: { select: { students: true, assignments: true } } },
    });
    res.json({ data: rows });
  }),
);

adminRouter.post(
  '/classes',
  ah(async (req, res) => {
    const { name } = classSchema.parse(req.body);
    const row = await prisma.class.create({ data: { adminId: adminId(req), name } });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'class.create', targetType: 'Class', targetId: row.id, ip: req.ip });
    res.status(201).json(row);
  }),
);

adminRouter.patch(
  '/classes/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const { name } = classSchema.parse(req.body);
    await ownClass(id, adminId(req));
    res.json(await prisma.class.update({ where: { id }, data: { name } }));
  }),
);

adminRouter.delete(
  '/classes/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownClass(id, adminId(req));
    // Students stay (classId becomes null); the class's exam assignments are removed.
    await prisma.class.delete({ where: { id } });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'class.delete', targetType: 'Class', targetId: id, ip: req.ip });
    res.status(204).end();
  }),
);

// ───────── Students ─────────
const studentSelect = {
  id: true,
  name: true,
  loginCode: true,
  status: true,
  mustChangePassword: true,
  classId: true,
  createdAt: true,
  class: { select: { id: true, name: true } },
} as const;

/** 7-digit codes are unique across all schools, so a student logs in without choosing a school. */
async function generateLoginCodes(count: number): Promise<string[]> {
  const codes = new Set<string>();
  for (let attempt = 0; attempt < 10 && codes.size < count; attempt++) {
    while (codes.size < count) codes.add(String(randomInt(0, 10_000_000)).padStart(7, '0'));
    const taken = await prisma.student.findMany({ where: { loginCode: { in: [...codes] } }, select: { loginCode: true } });
    for (const t of taken) codes.delete(t.loginCode);
  }
  if (codes.size < count) throw badRequest('CODE_EXHAUSTED', 'Gagal membuat kode login unik. Coba lagi.');
  return [...codes];
}

// The first password equals the login code; the student must change it at first sign-in.
async function newStudentRows(adminIdValue: string, items: { name: string; classId: string | null }[]) {
  const codes = await generateLoginCodes(items.length);
  const hashes: string[] = [];
  for (let i = 0; i < codes.length; i += 8) {
    hashes.push(...(await Promise.all(codes.slice(i, i + 8).map((c) => hashPassword(c)))));
  }
  return items.map((it, i) => ({
    adminId: adminIdValue,
    name: it.name,
    classId: it.classId,
    loginCode: codes[i],
    passwordHash: hashes[i],
    mustChangePassword: true,
  }));
}

const studentListQuery = pageSchema.extend({
  classId: z.string().uuid().optional(),
  unassigned: z.enum(['true', 'false']).optional(),
  q: z.string().trim().max(80).optional(),
});

adminRouter.get(
  '/students',
  ah(async (req, res) => {
    const { page, pageSize, classId, unassigned, q } = studentListQuery.parse(req.query);
    const where = {
      adminId: adminId(req),
      ...(classId ? { classId } : {}),
      ...(unassigned === 'true' ? { classId: null } : {}),
      ...(q ? { OR: [{ name: { contains: q, mode: 'insensitive' as const } }, { loginCode: { contains: q } }] } : {}),
    };
    const [total, data] = await Promise.all([
      prisma.student.count({ where }),
      prisma.student.findMany({
        where,
        orderBy: [{ name: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
        select: studentSelect,
      }),
    ]);
    res.json({ data, meta: { page, pageSize, total } });
  }),
);

const studentCreateSchema = z.object({
  name: z.string().trim().min(2, 'Nama minimal 2 karakter.').max(100),
  classId: z.string().uuid().nullable().optional(),
});

adminRouter.post(
  '/students',
  ah(async (req, res) => {
    const input = studentCreateSchema.parse(req.body);
    if (input.classId) await ownClass(input.classId, adminId(req));
    const [row] = await newStudentRows(adminId(req), [{ name: input.name, classId: input.classId ?? null }]);
    const created = await prisma.student.create({ data: row, select: studentSelect });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'student.create', targetType: 'Student', targetId: created.id, ip: req.ip });
    res.status(201).json(created);
  }),
);

// CSV is parsed in the browser; the server receives plain rows (100 per request keeps hashing fast).
const importSchema = z.object({
  students: z
    .array(z.object({ name: z.string().trim().min(2).max(100), className: z.string().trim().max(60).optional() }))
    .min(1)
    .max(100),
});

adminRouter.post(
  '/students/import',
  ah(async (req, res) => {
    const { students } = importSchema.parse(req.body);
    const id = adminId(req);

    const names = [...new Set(students.map((s) => s.className).filter((n): n is string => !!n))];
    const existing = await prisma.class.findMany({ where: { adminId: id, name: { in: names } } });
    const classByName = new Map(existing.map((c) => [c.name, c.id]));
    for (const name of names) {
      if (!classByName.has(name)) {
        const c = await prisma.class.create({ data: { adminId: id, name } });
        classByName.set(name, c.id);
      }
    }

    const rows = await newStudentRows(
      id,
      students.map((s) => ({ name: s.name, classId: s.className ? (classByName.get(s.className) ?? null) : null })),
    );
    const created = await prisma.$transaction(rows.map((data) => prisma.student.create({ data, select: studentSelect })));
    await audit({ actorType: 'ADMIN', actorId: id, action: 'student.import', ip: req.ip, metadata: { count: created.length } });
    res.status(201).json({ created: created.length, data: created });
  }),
);

const studentUpdateSchema = z.object({
  name: z.string().trim().min(2).max(100).optional(),
  classId: z.string().uuid().nullable().optional(),
  status: z.enum(['ACTIVE', 'DISABLED']).optional(),
});

adminRouter.patch(
  '/students/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const input = studentUpdateSchema.parse(req.body);
    await ownStudent(id, adminId(req));
    if (input.classId) await ownClass(input.classId, adminId(req));
    const updated = await prisma.student.update({ where: { id }, data: input, select: studentSelect });
    if (input.status === 'DISABLED') {
      await prisma.refreshToken.updateMany({ where: { studentId: id, revokedAt: null }, data: { revokedAt: new Date() } });
    }
    res.json(updated);
  }),
);

adminRouter.post(
  '/students/:id/reset-password',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    const s = await ownStudent(id, adminId(req));
    await prisma.$transaction([
      prisma.student.update({ where: { id }, data: { passwordHash: await hashPassword(s.loginCode), mustChangePassword: true } }),
      prisma.refreshToken.updateMany({ where: { studentId: id, revokedAt: null }, data: { revokedAt: new Date() } }),
    ]);
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'student.reset_password', targetType: 'Student', targetId: id, ip: req.ip });
    res.status(204).end();
  }),
);

adminRouter.delete(
  '/students/:id',
  ah(async (req, res) => {
    const { id } = idParam.parse(req.params);
    await ownStudent(id, adminId(req));
    if ((await prisma.examSession.count({ where: { studentId: id } })) > 0) {
      throw badRequest('HAS_EXAM_HISTORY', 'Siswa ini punya riwayat ujian. Nonaktifkan saja agar hasilnya tidak hilang.');
    }
    await prisma.student.delete({ where: { id } });
    await audit({ actorType: 'ADMIN', actorId: adminId(req), action: 'student.delete', targetType: 'Student', targetId: id, ip: req.ip });
    res.status(204).end();
  }),
);

adminRouter.use('/exams', examsRouter);
