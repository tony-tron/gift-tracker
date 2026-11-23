'use client';

import React, { useMemo, useState } from 'react';
import { Check, CheckSquare, Copy, Share2, Square, X } from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import type { WishItem } from '@/lib/types';

interface ShareWishlistSheetProps {
  wishes: WishItem[];
  onClose: () => void;
}

export function ShareWishlistSheet({ wishes, onClose }: ShareWishlistSheetProps) {
  const { showSnackbar } = useGiftStore();

  // Defaulting to all wishes selected as requested
  const [selectedIds, setSelectedIds] = useState<Set<string>>(
    () => new Set(wishes.map((w) => w.id))
  );
  const [includeUrls, setIncludeUrls] = useState(true);
  const [includeNotes, setIncludeNotes] = useState(true);
  const [copied, setCopied] = useState(false);

  const allSelected = selectedIds.size === wishes.length && wishes.length > 0;

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(wishes.map((w) => w.id)));
    }
  };

  const toggleWish = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const formattedShareText = useMemo(() => {
    const selectedWishes = wishes.filter((w) => selectedIds.has(w.id));
    if (selectedWishes.length === 0) return '';

    const lines = selectedWishes.map((w, idx) => {
      let line = `${idx + 1}. ${w.title}`;
      if (includeUrls && w.url?.trim()) {
        line += ` (${w.url.trim()})`;
      }
      if (includeNotes && w.notes?.trim()) {
        line += `\n   Note: ${w.notes.trim()}`;
      }
      return line;
    });

    return `🎁 My Gift Wishlist (${selectedWishes.length} item${selectedWishes.length === 1 ? '' : 's'}):\n\n${lines.join('\n\n')}`;
  }, [wishes, selectedIds, includeUrls, includeNotes]);

  const handleShare = async () => {
    if (!formattedShareText) return;
    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: 'My Gift Wishlist',
          text: formattedShareText,
        });
        onClose();
        return;
      } catch {
        // Fallback to clipboard
      }
    }
    await handleCopy();
  };

  const handleCopy = async () => {
    if (!formattedShareText) return;
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      await navigator.clipboard.writeText(formattedShareText);
      setCopied(true);
      showSnackbar(`Copied ${selectedIds.size} wish item${selectedIds.size === 1 ? '' : 's'} to clipboard!`);
      setTimeout(() => setCopied(false), 2200);
    }
  };

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="m3-sheet"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '580px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div className="m3-drag-handle" />

        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '12px' }}>
          <div>
            <span
              style={{
                fontSize: '11.5px',
                fontWeight: 700,
                color: 'var(--md-primary)',
                textTransform: 'uppercase',
                letterSpacing: '0.06em',
              }}
            >
              Share Personal Wishlist
            </span>
            <h2 style={{ fontSize: '20px', fontWeight: 800, marginTop: '2px' }}>
              Choose Wishes to Include
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', marginTop: '2px' }}>
              Select which wishlist ideas you want to share with friends and family.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="m3-btn-icon"
            style={{ width: '36px', height: '36px' }}
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        {/* Toolbar: Select All + Toggles */}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '8px',
            paddingBottom: '6px',
            borderBottom: '1px solid var(--md-outline-variant)',
          }}
        >
          <button
            type="button"
            onClick={toggleSelectAll}
            className="m3-btn-tonal"
            style={{ height: '32px', fontSize: '12.5px', padding: '0 12px' }}
          >
            {allSelected ? <Square size={14} /> : <CheckSquare size={14} />}
            <span>
              {allSelected ? 'Deselect All' : 'Select All'} ({selectedIds.size}/{wishes.length})
            </span>
          </button>

          <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <label
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '12.5px',
                cursor: 'pointer',
                userSelect: 'none',
              }}
            >
              <input
                type="checkbox"
                checked={includeNotes}
                onChange={(e) => setIncludeNotes(e.target.checked)}
                style={{ accentColor: 'var(--md-primary)', width: '15px', height: '15px' }}
              />
              Include notes
            </label>

            <label
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '12.5px',
                cursor: 'pointer',
                userSelect: 'none',
              }}
            >
              <input
                type="checkbox"
                checked={includeUrls}
                onChange={(e) => setIncludeUrls(e.target.checked)}
                style={{ accentColor: 'var(--md-primary)', width: '15px', height: '15px' }}
              />
              Include links
            </label>
          </div>
        </div>

        {/* Wish items checklist */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '8px',
            maxHeight: '260px',
            overflowY: 'auto',
            paddingRight: '4px',
          }}
        >
          {wishes.length === 0 ? (
            <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', padding: '16px 0' }}>
              Your wishlist has no active ideas to share yet.
            </p>
          ) : (
            wishes.map((w) => {
              const isSelected = selectedIds.has(w.id);
              return (
                <div
                  key={w.id}
                  onClick={() => toggleWish(w.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px',
                    padding: '10px 12px',
                    borderRadius: '16px',
                    background: isSelected
                      ? 'var(--md-secondary-container)'
                      : 'var(--md-surface-container)',
                    color: isSelected
                      ? 'var(--md-on-secondary-container)'
                      : 'var(--md-on-surface)',
                    cursor: 'pointer',
                    transition: 'all 180ms ease',
                    border: isSelected
                      ? '1px solid var(--md-secondary)'
                      : '1px solid transparent',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={() => toggleWish(w.id)}
                    onClick={(e) => e.stopPropagation()}
                    style={{
                      marginTop: '3px',
                      accentColor: 'var(--md-primary)',
                      width: '16px',
                      height: '16px',
                      cursor: 'pointer',
                    }}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: '14px', lineHeight: 1.3 }}>
                      {w.title}
                    </div>
                    {w.notes && (
                      <div
                        style={{
                          fontSize: '12px',
                          opacity: 0.85,
                          marginTop: '2px',
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        {w.notes}
                      </div>
                    )}
                    {w.url && (
                      <div
                        style={{
                          fontSize: '11.5px',
                          opacity: 0.7,
                          marginTop: '2px',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        🔗 {w.url}
                      </div>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Live Preview of shared text */}
        {selectedIds.size > 0 && (
          <div
            style={{
              background: 'var(--md-surface-container-highest)',
              borderRadius: '14px',
              padding: '10px 14px',
              fontSize: '12px',
              maxHeight: '120px',
              overflowY: 'auto',
              fontFamily: 'monospace',
              whiteSpace: 'pre-wrap',
              color: 'var(--md-on-surface)',
              border: '1px solid var(--md-outline-variant)',
            }}
          >
            {formattedShareText}
          </div>
        )}

        {/* Action Buttons */}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: '10px',
            marginTop: '4px',
          }}
        >
          <button type="button" onClick={onClose} className="m3-btn-text">
            Cancel
          </button>

          <button
            type="button"
            onClick={() => void handleCopy()}
            disabled={selectedIds.size === 0}
            className="m3-btn-tonal"
            style={{ opacity: selectedIds.size === 0 ? 0.5 : 1 }}
          >
            {copied ? <Check size={15} /> : <Copy size={15} />}
            {copied ? 'Copied!' : 'Copy to Clipboard'}
          </button>

          <button
            type="button"
            onClick={() => void handleShare()}
            disabled={selectedIds.size === 0}
            className="m3-btn-filled"
            style={{ opacity: selectedIds.size === 0 ? 0.5 : 1 }}
          >
            <Share2 size={15} />
            Share ({selectedIds.size})
          </button>
        </div>
      </div>
    </div>
  );
}
