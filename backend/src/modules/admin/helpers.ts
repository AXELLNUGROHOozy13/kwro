import type { Request } from 'express';
import { z } from 'zod';
import { prisma } from '../../lib/db';
import { notFound } from '../../lib/errors';

export const adminId = (req: Request): string => req.auth!.id;

export const idParam = z.object({ id: z.string().uuid() });

export const pageSchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(200).default(50),
});

/** Every admin query is scoped by adminId, so one school can never read or change another school's data. */
export async function ownClass(id: string, admin: string) {
  const row = await prisma.class.findFirst({ where: { id, adminId: admin } });
  if (!row) throw notFound('Kelas tidak ditemukan.');
  return row;
}

export async function ownExam(id: string, admin: string) {
  const row = await prisma.exam.findFirst({ where: { id, adminId: admin } });
  if (!row) throw notFound('Ujian tidak ditemukan.');
  return row;
}

export async function ownStudent(id: string, admin: string) {
  const row = await prisma.student.findFirst({ where: { id, adminId: admin } });
  if (!row) throw notFound('Siswa tidak ditemukan.');
  return row;
}
