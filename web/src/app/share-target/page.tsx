'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Loader2 } from 'lucide-react';
import { GiftTrackerApp } from '@/components/GiftTrackerApp';

function ShareTargetReceiver() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [draft, setDraft] = useState<{
    title?: string;
    url?: string;
    store?: string;
    cost?: number | null;
  } | null>(null);
  const [resolving, setResolving] = useState(true);

  useEffect(() => {
    const rawTitle = searchParams.get('title') || '';
    const rawText = searchParams.get('text') || '';
    const rawUrl = searchParams.get('url') || '';

    // Android apps (like Amazon & Target) frequently embed the URL inside `text` rather than `url`
    const combined = `${rawUrl} ${rawText} ${rawTitle}`;
    const urlMatch = combined.match(/https?:\/\/[^\s"'<>]+/i);
    const extractedUrl = urlMatch ? urlMatch[0] : '';

    const cleanTextTitle = rawTitle || rawText.replace(/https?:\/\/[^\s]+/gi, '').trim();

    if (!extractedUrl && !cleanTextTitle) {
      router.replace('/');
      return;
    }

    let cancelled = false;
    async function hydrateLink() {
      if (!extractedUrl) {
        if (!cancelled) {
          setDraft({ title: cleanTextTitle });
          setResolving(false);
        }
        return;
      }

      try {
        const res = await fetch(`/api/scrape-link?url=${encodeURIComponent(extractedUrl)}`);
        if (res.ok) {
          const meta = (await res.json()) as {
            url: string;
            title: string;
            store: string;
            price: number | null;
          };
          if (!cancelled) {
            setDraft({
              url: meta.url || extractedUrl,
              title: meta.title || cleanTextTitle,
              store: meta.store || '',
              cost: meta.price,
            });
            setResolving(false);
          }
          return;
        }
      } catch {
        // Fallback if offline
      }

      if (!cancelled) {
        setDraft({
          url: extractedUrl,
          title: cleanTextTitle,
        });
        setResolving(false);
      }
    }

    void hydrateLink();
    return () => {
      cancelled = true;
    };
  }, [searchParams, router]);

  if (resolving) {
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '12px',
          background: 'var(--md-bg)',
          color: 'var(--md-on-surface)',
        }}
      >
        <Loader2 size={28} style={{ color: 'var(--md-primary)' }} />
        <p style={{ fontWeight: 600, fontSize: '15px' }}>
          Importing shared link into Gift Ideas...
        </p>
      </div>
    );
  }

  return <GiftTrackerApp initialSharedDraft={draft} />;
}

export default function ShareTargetPage() {
  return (
    <Suspense fallback={<GiftTrackerApp />}>
      <ShareTargetReceiver />
    </Suspense>
  );
}
