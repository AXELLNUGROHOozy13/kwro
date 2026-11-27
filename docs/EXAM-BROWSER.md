# KawRexam Exam Browser (mode "Ujian Web")

Tujuan: guru bisa membuat ujian yang soalnya ada di **situs lain** (LMS sekolah, Moodle, Google Form tanpa login,
Quizizz/Wayground, situs buatan sendiri), tetapi siswa tetap mengerjakannya **di dalam KawRexam yang terkunci**,
seperti Safe Exam Browser. KawRexam tidak menilai apa pun di mode ini: ia hanya mengunci, mengawasi, dan mengatur waktu.

## 1. Keputusan desain

| Keputusan | Alasan |
|---|---|
| Mode per ujian (`NATIVE` / `WEB`), bukan aplikasi baru | Semua kunci yang sudah ada langsung berlaku: screen pinning, VPN penutup jaringan, FLAG_SECURE, deteksi keluar aplikasi, penalti, PIN pengawas, token ujian, mode Jangan Ganggu. Tidak ada kode keamanan yang diduplikasi. |
| Situs dibuka di `WebView` **di dalam proses KawRexam** | VPN penutup jaringan mengecualikan KawRexam, jadi hanya WebView ini yang punya internet. Aplikasi lain tetap terputus. |
| Daftar situs yang diizinkan (allowlist) dikelola guru di dashboard | Alamat awal otomatis diizinkan (beserta subdomain). Situs pendukung (login, CDN soal, dll.) ditambah manual. |
| Hanya **navigasi halaman utama** yang dibatasi ke allowlist; sub-resource (gambar, skrip, font) hanya wajib HTTPS | Membatasi semua sub-resource membuat banyak situs rusak (CDN, font, analytics). Risikonya rendah: siswa tidak bisa mengetik alamat sendiri. |
| Selesai tetap lewat **PIN pengawas**; waktu habis = kumpul otomatis | Sama persis dengan ujian biasa, jadi pengawas tidak perlu belajar alur baru. |
| Tidak ada nilai otomatis | Server tidak punya soal/kunci jawaban. Sesi hanya berstatus SUBMITTED. Nilai diinput guru dari sistem situs ujian. |

## 2. Alur

```
Guru (dashboard)                Server                         HP siswa
─────────────────               ──────                         ────────
Buat ujian, jenis "Ujian Web"
Isi alamat https://... + situs
tambahan, pilih kelas, Mulai ─► status ACTIVE
                                                               Daftar ujian → Checklist (DND, VPN, token)
                                POST /exams/:id/start  ◄────── Token benar
                                payload {mode:"WEB", webUrl, ...} ─►  screen pinning + VPN + WebView
                                                               Siswa mengerjakan di situs
                                heartbeat / violations  ◄────► sama seperti ujian biasa
Pengawas beri PIN               POST /sessions/:id/submit(pin) ◄ "Selesai" + PIN, atau waktu habis
                                status SUBMITTED (tanpa nilai) ─► layar "Ujian selesai"
```

## 3. Perubahan data dan API

**Prisma (`Exam`)**: `mode ExamMode @default(NATIVE)`, `webUrl String?`, `webAllowedHosts String[]`,
`webLockClipboard Boolean @default(true)`. Migrasi: `npx prisma migrate dev --name exam_browser`.

**Admin API** (`/exams`): field baru pada buat/ubah ujian. `webUrl` wajib `https://`. `webAllowedHosts` dinormalisasi
(huruf kecil, tanpa skema/path, divalidasi). `mode` terkunci setelah ada siswa yang mulai. Ujian web boleh
dipublikasikan tanpa soal asalkan `webUrl` terisi.

**Student API**: `GET /exams` menambah `mode`. `POST /exams/:id/start` dan `GET /sessions/:id` menambah
`mode`, `webUrl`, `webAllowedHosts`, `webLockClipboard`. `finalize()` melewati penilaian untuk mode WEB;
`/submit` tidak memeriksa "semua soal terjawab" untuk mode WEB (PIN tetap wajib kecuali waktu habis).

## 4. Aplikasi Android

- `ExamBrowser.kt`: layar WebView + `HostPolicy` (aturan allowlist).
- `ExamScreen.kt`: bila `session.mode == "WEB"` memakai `ExamBrowserScreen`; overlay penalti, "Mengirim…",
  dan dialog peringatan dipakai bersama dengan ujian biasa.
- Layar: bar atas (judul, **host yang sedang dibuka**, timer), WebView, bar bawah (Kembali, Maju, Muat ulang, Selesai).
  Host selalu terlihat supaya siswa/pengawas bisa mengenali situs palsu.

### Pengerasan WebView
| Ancaman | Penanganan |
|---|---|
| Siswa pindah ke situs lain lewat tautan/redirect | `shouldOverrideUrlLoading` + `shouldInterceptRequest` (main frame) memeriksa allowlist; ditolak dengan pesan |
| Trik `https://diizinkan.com@jahat.com` | Tautan dengan `userInfo` ditolak; URL yang diperiksa sudah dikanonkan oleh Chromium |
| `http`, `file`, `content`, `intent`, `market`, `tel` | Hanya `https` (plus `data`, `blob`, `about:blank`) |
| Pop-up / jendela baru | `supportMultipleWindows=false`; tautan `_blank` dimuat di tampilan yang sama (tetap lewat allowlist) |
| Unduh file | `DownloadListener` menolak |
| Akses berkas HP | `allowFileAccess=false`, `allowContentAccess=false`, tombol unggah (file chooser) dibatalkan |
| Sertifikat HTTPS rusak | `onReceivedSslError` → batal |
| Konten campuran | `MIXED_CONTENT_NEVER_ALLOW` |
| Kamera/mikrofon/lokasi dari situs | Ditolak |
| Salin/tempel/blok teks | Opsional (`webLockClipboard`): CSS + event JS, tekan-lama dinonaktifkan, clipboard dikosongkan di awal |
| Isi otomatis (password manager) | `importantForAutofill=NO_EXCLUDE_DESCENDANTS` |
| Tangkapan layar / rekam / cast | `FLAG_SECURE` (sudah ada), deteksi layar kedua (sudah ada) |
| Sisa login di HP bersama | Cookie, storage, cache, riwayat dihapus saat layar ujian ditutup |
| Situs mengenali browser ujian | User-Agent ditambah `KawRexam/1.0` (bisa dipakai situs untuk mewajibkan KawRexam) |

### Penyesuaian pelanggaran
`<select>` dan kotak tanggal di WebView membuka dialog sistem yang mengambil fokus jendela. Pada mode web,
hilang fokus **tanpa meninggalkan aplikasi** baru dihitung pelanggaran bila ≥ 8 detik (biasa: 3 detik).
Keluar aplikasi sungguhan (`onPause`) tetap 3 detik.

## 5. Batasan yang jujur

1. **Login Google tidak jalan di WebView.** Google memblokir login di WebView. Google Form yang mewajibkan login
   akan gagal; Google Form tanpa login, Moodle, LMS sekolah, Quizizz/Wayground, dan situs sendiri biasanya aman.
2. **Unggah berkas dimatikan** (v1). Soal yang meminta unggah foto/dokumen tidak bisa dipakai.
3. **Isi halaman tidak bisa dijamin.** Server tidak melihat apa yang diketik siswa di situs lain; yang dijamin hanya
   *dari mana* soal dibuka, *kapan*, dan bahwa HP tetap terkunci. Hasil dinilai di situs ujian itu sendiri.
4. **Muat ulang aplikasi** (mis. aplikasi dimatikan paksa lalu dibuka) memuat ulang alamat awal; progres di situs
   bergantung pada situs itu (Google Form menyimpan draf, LMS biasanya lanjut).
5. **Perlu uji di perangkat nyata**: perilaku WebView di balik VPN penutup, dan keyboard/dialog tiap merek HP.

## 6. Daftar uji sebelum dipakai

- [ ] Buat ujian web, isi alamat `https://...`, pilih kelas, mulai.
- [ ] HP siswa: Checklist → token → situs terbuka, bar host menampilkan domain benar.
- [ ] Tautan ke domain lain diblokir (muncul pesan kuning), halaman tidak berpindah.
- [ ] Aplikasi lain tidak punya internet selama ujian (VPN aktif); WebView tetap memuat.
- [ ] Keluar aplikasi / tarik panel / layar terpisah → pelanggaran + kunci penalti, sama seperti ujian biasa.
- [ ] Lepas screen pinning → sesi ditangguhkan.
- [ ] "Selesai" + PIN salah ditolak; PIN benar → layar selesai; di dashboard status SUBMITTED.
- [ ] Waktu habis → kumpul otomatis tanpa PIN.
- [ ] Salin/tempel diblokir bila opsinya aktif; mengetik jawaban tetap bisa.
- [ ] Setelah selesai, buka lagi ujian lain: tidak ada sisa login ujian sebelumnya.

## 7. Usulan fitur berikutnya (berurutan menurut manfaat/biaya)

1. **Pemantauan langsung** di dashboard: daftar siswa dengan status, jumlah pelanggaran, dan sisa waktu yang
   diperbarui otomatis (datanya sudah ada lewat heartbeat).
2. **Token sekali pakai per siswa** atau token yang berganti tiap N menit (anti-bocor ke luar ruangan).
3. **Ekspor rekap** (CSV/Excel) per ujian: peserta, status, pelanggaran, waktu mulai/selesai; untuk mode web
   ditambah kolom nilai yang diisi guru.
4. **Input nilai manual** untuk ujian web (guru menempel nilai dari situs ujian).
5. **Pengunci versi aplikasi**: server menolak versi KawRexam lama (wajib update sebelum ujian).
6. **Foto verifikasi** (selfie saat mulai) untuk ujian bernilai tinggi. Perlu persetujuan sekolah/orang tua.
7. **Unggah berkas terbatas** di mode web (hanya foto kamera, tanpa akses galeri).
