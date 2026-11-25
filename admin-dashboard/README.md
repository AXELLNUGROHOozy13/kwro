# KAWREXAM Admin Dashboard

Next.js 14 (App Router) untuk guru/administrator sekolah: kelas, siswa, ujian, soal, peserta, hasil.

## Deploy ke Vercel
1. Import repo ini, **Root Directory = `admin-dashboard`** (framework Next.js terdeteksi otomatis).
2. Environment Variables: `BACKEND_URL` = alamat publik backend Railway, mis. `https://nama-backend.up.railway.app`
   (tanpa `/api`, tanpa garis miring di akhir).
3. Deploy. Kalau `BACKEND_URL` diubah, **Redeploy**: nilainya dibaca saat build.
4. Di Railway (backend) `COOKIE_SECURE=true`. `CORS_ORIGINS` tetap kosong: browser hanya bicara ke domain Vercel,
   Next.js yang meneruskan `/api/*` ke backend.

## Jalan lokal
```bash
cd admin-dashboard
cp .env.example .env.local   # isi BACKEND_URL
npm install
npm run dev                  # http://localhost:3002
```

## Alur pakai
Daftar di `/register` dengan kode aktivasi dari Developer Console → buat kelas → tambah/impor siswa → buat ujian →
isi soal → pilih kelas peserta → Mulai sekarang.

## Impor CSV
- Siswa: kolom `nama,kelas` (kelas yang belum ada dibuat otomatis). Delimiter `,` atau `;`.
- Soal: pakai template dari tombol "Unduh template" (tipe PG, PGK, BS, ISIAN, ESAI).
- XLSX/DOCX/Google Sheets belum ada. Simpan Excel sebagai CSV dulu.

## Belum ada (sengaja, fase berikutnya)
Penilaian otomatis dan hasil siswa (Phase 3), monitoring real-time dan kontrol pelanggaran (Phase 5),
urutkan ulang soal, upload gambar soal, notifikasi.
