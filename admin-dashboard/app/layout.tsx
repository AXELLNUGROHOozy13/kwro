import type { Metadata, Viewport } from 'next';
import { IBM_Plex_Sans, Source_Serif_4 } from 'next/font/google';
import './globals.css';

const sans = IBM_Plex_Sans({ subsets: ['latin'], weight: ['400', '500', '600'], variable: '--font-sans', display: 'swap' });
const serif = Source_Serif_4({ subsets: ['latin'], weight: ['500', '600', '700'], variable: '--font-serif', display: 'swap' });

export const metadata: Metadata = {
  title: 'KAWREXAM Admin',
  description: 'Kelola kelas, siswa, soal, dan ujian KAWREXAM.',
  robots: { index: false, follow: false },
};
export const viewport: Viewport = { width: 'device-width', initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="id" className={`${sans.variable} ${serif.variable}`}>
      <body>{children}</body>
    </html>
  );
}
