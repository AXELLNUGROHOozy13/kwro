# Cara pasang: Exam Browser

1. Timpa file dari zip ini ke project (struktur folder sama). Rencana lengkap: docs/EXAM-BROWSER.md
2. Backend: `cd backend && npx prisma migrate dev --name exam_browser` (atau `migrate deploy` di server), lalu restart.
3. Dashboard admin: deploy ulang (Vercel).
4. Android: build ulang APK. APK lama tetap bisa untuk ujian biasa, tapi tidak bisa membuka ujian web.
5. Uji pakai daftar di docs/EXAM-BROWSER.md bagian 6 SEBELUM dipakai ujian sungguhan.
