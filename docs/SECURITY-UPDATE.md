# Update keamanan

## Isi
1. **Kunci akun otomatis** (siswa, admin, developer): 5 salah berturut-turut mengunci akun. Siswa 5, 15, lalu 60 menit; admin/developer 15 menit, 1 jam, lalu 24 jam. Hitungan salah dilupakan setelah 30 menit tanpa kesalahan. Akun terkunci tidak mendapat cek kata sandi gratis. Pengawas bisa membuka kunci siswa: `POST /api/v1/admin/security/students/:id/unlock`.
2. **MFA admin (authenticator / TOTP)**: opsional, diaktifkan di menu `/security`. Rahasia disimpan terenkripsi (AES-256-GCM, kunci diturunkan dari `CODE_HASH_PEPPER`). Kode tidak bisa dipakai dua kali, ada 8 kode pemulihan sekali pakai. Mematikan MFA butuh kata sandi + kode.
3. **Keluar paksa dari pin = akun ditangguhkan**: sesi jadi `STOPPED` (alasan `PIN_EXITED`), semua login siswa dicabut, HP menutup ujian dan keluar. Hanya pengawas yang bisa membuka lewat tombol **Buka kunci** di tab Hasil/peserta (opsional tambah waktu). Lalu siswa **wajib masuk ulang**.
4. **Kunci jaringan (VPN lokal)**: semua aplikasi lain diputus internetnya selama ujian, hanya KAWREXAM yang online. Wajib aktif di checklist. Dimatikan di tengah ujian = pelanggaran `VPN_DISABLED`.
5. **Deteksi layar kedua / cast**: layar eksternal (HDMI, USB-C, Miracast) = pelanggaran `SCREEN_CAST`.
6. **Perbaikan bug**: dialog peringatan/konfirmasi di dalam ujian mengambil fokus jendela, jadi membacanya lebih dari 3 detik tercatat sebagai "keluar dari ujian". Sekarang dialog milik aplikasi dikenali.

## Cara pasang
1. Ekstrak zip ini di atas repo (timpa file yang sama).
2. Backend: `npx prisma db push` (atau biarkan docker-entrypoint yang menjalankannya), lalu deploy ulang. Tidak ada variable baru. **Jangan ganti `CODE_HASH_PEPPER`**: sekarang dipakai juga untuk membuka rahasia MFA.
3. Admin dashboard: tambahkan link ke `/security` di menu (file `Shell` tidak ada di zip ini).
4. APK: build ulang lewat GitHub Actions. Perlu install ulang di HP siswa.

## Kontrak login admin dengan MFA (halaman login belum diubah)
- `POST /api/v1/auth/admin/login` -> jika MFA aktif: `{ "mfaRequired": true, "mfaToken": "..." }` (tanpa sesi).
- `POST /api/v1/auth/admin/login/mfa` body `{ mfaToken, code }` -> sama seperti login biasa (token + cookie). `code` = 6 angka authenticator atau kode pemulihan. `mfaToken` berlaku 5 menit.

## Batas yang jujur
- Kunci jaringan hanya memutus HP ini. HP kedua, hotspot dari HP lain, atau bantuan orang lain tetap tidak terdeteksi.
- Deteksi cast hanya melihat layar eksternal yang terdaftar di Android. Mirroring lewat perekam layar/MediaProjection memakai layar virtual privat yang tidak terlihat aplikasi; yang menahannya adalah `FLAG_SECURE` (hasil rekaman hitam). Foldable dengan layar luar belum dites, kemungkinan false positive; pelanggaran bisa dibatalkan pengawas.
- Kunci akun bisa disalahgunakan: orang yang tahu kode 7 digit sebelum ujian bisa sengaja salah sandi untuk mengunci temannya. Karena itu kunci siswa pendek dan pengawas bisa membukanya.
- Jika HP offline saat keluar dari pin, penangguhan baru sampai ke server saat tersambung. Di HP, ujian tetap langsung tertutup dan tidak bisa dibuka lagi tanpa melapor.
- Belum dites: seluruh kode ini belum pernah dikompilasi atau dijalankan (tidak ada Gradle, npm, atau database di lingkungan penulisan).
