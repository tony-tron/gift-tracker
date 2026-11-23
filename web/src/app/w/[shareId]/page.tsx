import React, { Suspense } from 'react';
import type { Metadata } from 'next';
import { SharedWishlistViewer } from '@/components/views/SharedWishlistViewer';

export const metadata: Metadata = {
  title: 'Shared Gift Wishlist — Collaborative Checklist',
  description:
    'Coordinate gifts together in real time without spoiling the surprise for the wishlist owner.',
};

function WishlistLoadingShell() {
  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '10px',
        background: 'var(--md-bg)',
        color: 'var(--md-on-surface)',
      }}
    >
      <p style={{ fontWeight: 600, fontSize: '15px' }}>Loading shared wishlist...</p>
    </div>
  );
}

export default function SharedWishlistPage() {
  return (
    <Suspense fallback={<WishlistLoadingShell />}>
      <SharedWishlistViewer />
    </Suspense>
  );
}
