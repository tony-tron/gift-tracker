'use client';

import React, { useMemo, useState } from 'react';
import { Check, Copy, ExternalLink, Gift, Plus, Share2, Trash2 } from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';

export function WishlistView() {
  const { wishes, addWish, updateWish, deleteWish, showSnackbar } = useGiftStore();

  const [filter, setFilter] = useState<'wanted' | 'received'>('wanted');
  const [newTitle, setNewTitle] = useState('');
  const [newUrl, setNewUrl] = useState('');
  const [editingReceivedId, setEditingReceivedId] = useState<string | null>(null);
  const [fromInput, setFromInput] = useState('');
  const [whenInput, setWhenInput] = useState('Christmas 2026');

  const wantedWishes = useMemo(() => wishes.filter((w) => !w.received), [wishes]);
  const receivedWishes = useMemo(() => wishes.filter((w) => w.received), [wishes]);

  const displayed = filter === 'wanted' ? wantedWishes : receivedWishes;

  const handleAddWish = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    addWish({
      title: newTitle.trim(),
      url: newUrl.trim(),
      notes: '',
      received: false,
      receivedFrom: '',
      receivedWhen: '',
      priority: 'medium',
    });
    setNewTitle('');
    setNewUrl('');
  };

  const handleShareWishlist = async () => {
    const lines = wantedWishes.map(
      (w, idx) => `${idx + 1}. ${w.title}${w.url ? ` (${w.url})` : ''}`
    );
    const formatted = `🎁 My Gift Wishlist:\n\n${lines.join('\n')}`;

    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: 'My Gift Wishlist',
          text: formatted,
        });
        return;
      } catch {
        // Fallback to clipboard
      }
    }
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      await navigator.clipboard.writeText(formatted);
      showSnackbar('Copied formatted wishlist to clipboard!');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '18px', maxWidth: '920px' }}>
      <div
        className="m3-hero-card"
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '14px',
        }}
      >
        <div>
          <span
            style={{
              fontSize: '12px',
              fontWeight: 700,
              color: 'var(--md-primary)',
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
            }}
          >
            Personal Wishlist (&ldquo;Me&rdquo; Tab)
          </span>
          <h2 style={{ fontSize: '22px', fontWeight: 700, marginTop: '2px' }}>
            {wantedWishes.length} Active Wishes • {receivedWishes.length} Received
          </h2>
          <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', marginTop: '2px' }}>
            Keep ideas for yourself handy and share a clean list when family asks what you want.
          </p>
        </div>

        <button type="button" onClick={() => void handleShareWishlist()} className="m3-btn-filled">
          {typeof navigator !== 'undefined' && 'share' in navigator ? (
            <>
              <Share2 size={15} /> Share Wishlist
            </>
          ) : (
            <>
              <Copy size={15} /> Copy Wishlist
            </>
          )}
        </button>
      </div>

      {/* Quick Add Wish Form */}
      <form
        onSubmit={handleAddWish}
        className="m3-surface-card"
        style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}
      >
        <input
          type="text"
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="Add something to your wishlist (e.g., Switch 2 game, coffee beans)..."
          className="m3-input"
          style={{ flex: '2 1 240px' }}
        />
        <input
          type="text"
          value={newUrl}
          onChange={(e) => setNewUrl(e.target.value)}
          placeholder="Optional link..."
          className="m3-input"
          style={{ flex: '1 1 160px' }}
        />
        <button type="submit" className="m3-btn-filled">
          <Plus size={16} /> Add Wish
        </button>
      </form>

      {/* Segmented Filter */}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          type="button"
          className="m3-chip"
          data-selected={filter === 'wanted'}
          onClick={() => setFilter('wanted')}
        >
          ⭐ Wishes ({wantedWishes.length})
        </button>
        <button
          type="button"
          className="m3-chip"
          data-selected={filter === 'received'}
          onClick={() => setFilter('received')}
        >
          <Gift size={14} /> Received Log ({receivedWishes.length})
        </button>
      </div>

      {/* Wishes List */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {displayed.map((wish) => {
          const isLogging = editingReceivedId === wish.id;
          return (
            <div key={wish.id} className="m3-surface-card" style={{ padding: '14px 16px' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '12px',
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <h3
                    style={{
                      fontSize: '15.5px',
                      fontWeight: 700,
                      textDecoration: wish.received ? 'line-through' : 'none',
                      opacity: wish.received ? 0.78 : 1,
                    }}
                  >
                    {wish.title}
                  </h3>
                  <div
                    style={{
                      display: 'flex',
                      flexWrap: 'wrap',
                      gap: '10px',
                      marginTop: '3px',
                      fontSize: '12.5px',
                      color: 'var(--md-on-surface-variant)',
                    }}
                  >
                    {wish.received && (wish.receivedFrom || wish.receivedWhen) && (
                      <span style={{ color: 'var(--md-success)', fontWeight: 600 }}>
                        ✓ From {wish.receivedFrom || 'Family'}
                        {wish.receivedWhen ? ` (${wish.receivedWhen})` : ''}
                      </span>
                    )}
                    {wish.url && (
                      <a
                        href={wish.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{
                          color: 'var(--md-primary)',
                          fontWeight: 600,
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <ExternalLink size={12} /> Link
                      </a>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
                  {!wish.received ? (
                    <button
                      type="button"
                      onClick={() => {
                        setEditingReceivedId(isLogging ? null : wish.id);
                        setFromInput('');
                        setWhenInput('Christmas 2026');
                      }}
                      className="m3-btn-tonal"
                      style={{ height: '36px', fontSize: '12.5px' }}
                    >
                      <Check size={14} /> Mark Received
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() =>
                        updateWish(wish.id, {
                          received: false,
                          receivedFrom: '',
                          receivedWhen: '',
                        })
                      }
                      className="m3-btn-tonal"
                      style={{ height: '34px', fontSize: '12px' }}
                    >
                      Move to Wishes
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => deleteWish(wish.id)}
                    className="m3-btn-icon"
                    style={{ width: '34px', height: '34px' }}
                    aria-label="Delete wish"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>

              {isLogging && (
                <div
                  style={{
                    marginTop: '12px',
                    paddingTop: '12px',
                    borderTop: '1px solid var(--md-outline-variant)',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '8px',
                    alignItems: 'center',
                  }}
                >
                  <input
                    type="text"
                    value={fromInput}
                    onChange={(e) => setFromInput(e.target.value)}
                    placeholder="From who? (e.g., Alex, Dad)"
                    className="m3-input"
                    style={{ flex: '1 1 160px', height: '38px' }}
                  />
                  <input
                    type="text"
                    value={whenInput}
                    onChange={(e) => setWhenInput(e.target.value)}
                    placeholder="Occasion (e.g., Christmas 2026)"
                    className="m3-input"
                    style={{ flex: '1 1 160px', height: '38px' }}
                  />
                  <button
                    type="button"
                    onClick={() => {
                      updateWish(wish.id, {
                        received: true,
                        receivedFrom: fromInput.trim(),
                        receivedWhen: whenInput.trim(),
                      });
                      setEditingReceivedId(null);
                    }}
                    className="m3-btn-filled"
                    style={{ height: '38px' }}
                  >
                    Confirm
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
