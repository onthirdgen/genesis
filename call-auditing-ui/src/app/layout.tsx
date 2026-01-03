import type { Metadata } from 'next';
import { Inter, JetBrains_Mono } from 'next/font/google';
import { Providers } from '@/components/providers';
import '@/styles/globals.css';

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-sans',
  display: 'swap',
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ['latin'],
  variable: '--font-mono',
  display: 'swap',
});

export const metadata: Metadata = {
  title: {
    default: 'Call Auditing Platform',
    template: '%s | Call Auditing Platform',
  },
  description:
    'Voice of the Customer platform for analyzing call recordings, extracting insights, and ensuring compliance.',
  keywords: [
    'call auditing',
    'voice of customer',
    'sentiment analysis',
    'compliance',
    'transcription',
  ],
  authors: [{ name: 'Call Auditing Team' }],
  creator: 'Call Auditing Team',
  openGraph: {
    type: 'website',
    locale: 'en_US',
    url: 'https://call-auditing.example.com',
    title: 'Call Auditing Platform',
    description: 'Voice of the Customer platform for call analysis and insights',
    siteName: 'Call Auditing Platform',
  },
  robots: {
    index: false, // Set to true in production
    follow: false,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${inter.variable} ${jetbrainsMono.variable} font-sans antialiased`}
      >
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
