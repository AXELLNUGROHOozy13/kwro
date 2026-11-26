# Token ujian

1. Salin file zip ini ke atas project (timpa).
2. Backend: `cd backend && npx prisma migrate dev --name exam_token` (menambah kolom `examToken`), lalu restart.
   Ujian lama otomatis dapat token saat halaman detailnya dibuka di dashboard admin.
3. Build ulang APK. Aplikasi lama tidak bisa memulai ujian lagi karena server sekarang mewajibkan token.
4. Dashboard admin: halaman detail ujian > "Token ujian" (tombol "Buat token baru").
