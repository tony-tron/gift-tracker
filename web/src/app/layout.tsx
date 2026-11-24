import type { Metadata, Viewport } from 'next';
import { Plus_Jakarta_Sans, Fraunces, JetBrains_Mono } from 'next/font/google';
import './globals.css';
import { GiftStoreProvider } from '@/context/GiftStoreContext';
import { ServiceWorkerRegistrar } from '@/components/ServiceWorkerRegistrar';

const jakartaSans = Plus_Jakarta_Sans({
  variable: '--font-sans',
  subsets: ['latin'],
  display: 'swap',
});

const frauncesDisplay = Fraunces({
  variable: '--font-display',
  subsets: ['latin'],
  display: 'swap',
});

const jetbrainsMono = JetBrains_Mono({
  variable: '--font-mono',
  subsets: ['latin'],
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Gift Tracker',
  description:
    'Personal gift tracking app for Android phones, foldables, and Googlebook with real-time Firebase sync.',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'black-translucent',
    title: 'Gift Tracker',
  },
  icons: {
    icon: '/icons/icon-192.png',
    apple: '/apple-touch-icon.png',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: [
    { media: '(prefers-color-scheme: dark)', color: '#16110f' },
    { media: '(prefers-color-scheme: light)', color: '#fff8f6' },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      data-seed="terracotta"
      data-theme="dark"
      suppressHydrationWarning
      className={`${jakartaSans.variable} ${frauncesDisplay.variable} ${jetbrainsMono.variable}`}
    >
      <body>
        <GiftStoreProvider>
          <ServiceWorkerRegistrar />
          {children}
        </GiftStoreProvider>
      </body>
    </html>
  );
}
