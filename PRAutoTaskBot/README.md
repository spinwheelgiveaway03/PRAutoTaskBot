# PR Auto Task Bot

Aplikasi Android pendamping untuk mengelola microtask dari **PR.SOCIAL** menggunakan akun media sosial **pribadi** kamu.

## Alur kerja yang didukung (sesuai video kamu)

1. Ambil tugas Follow di PR.SOCIAL
2. Masukkan username target ke aplikasi (atau pakai demo)
3. START antrean / Floating Bubble
4. Bubble menampilkan tugas aktif → **BUKA TARGET** (langsung buka profil Instagram)
5. Kamu tekan **Ikuti** di Instagram (akun pribadi)
6. Kembali ke bubble → tekan **SELESAI**
7. Antrean otomatis ambil Follow berikutnya
8. Follow habis → otomatis pindah ke Like
9. Semua habis → Monitoring aktif (60–120 detik) → buka PR.SOCIAL lagi untuk misi baru

## Fitur lengkap

- Multi-platform dashboard (Instagram siap)
- Floating Bubble (geser, START / PAUSE / STOP / SELESAI / GAGAL / BUKA TARGET / BUKA PR.SOCIAL)
- Smart Queue prioritas: Follow → Like → Comment → Other
- Dynamic monitoring saat antrean kosong
- Room Database + Riwayat
- DataStore Settings (interval, notifikasi, tema)
- Material 3 Dark mode
- GitHub Actions build APK

## Cara dapat APK hanya dari HP

1. Download ZIP proyek ini
2. Buat repository baru di github.com (Public)
3. Upload semua isi folder PRAutoTaskBot
4. Tab **Actions** → **Build Debug APK** → **Run workflow**
5. Tunggu hijau → download artifact **PRAutoTaskBot-debug-apk**
6. Extract → install APK

## Izin yang dibutuhkan

- Tampilkan di atas aplikasi lain (Overlay) → Floating Bubble
- Notifikasi
- Internet

## Catatan penting

- Akun Instagram = akun pribadi kamu.
- Aplikasi mengelola antrean + membuka target + mencatat hasil.
- Tekan tombol Ikuti di Instagram dilakukan oleh kamu (satu ketukan).
- Ini membuat proses sangat cepat sambil mengurangi risiko rate-limit Instagram.

---
Versi 1.0.0 – Siap di-build lewat GitHub Actions.
