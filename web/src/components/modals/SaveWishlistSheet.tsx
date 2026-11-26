'use client';

import React, { useEffect, useMemo, useState } from 'react';
import {
  Check,
  ExternalLink,
  Link2,
  Loader2,
  Sparkles,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { fetchPublicWishlistOnce, parseWishlistUrl } from '@/lib/firebase';
import type { SavedWishlist } from '@/lib/types';

interface SaveWishlistSheetProps {
  open: boolean;
  onClose: () => void;
  editingWishlist?: SavedWishlist | null;
  defaultPersonId?: string | null;
  defaultUrl?: string;
}

export function SaveWishlistSheet({
  open,
  onClose,
  editingWishlist,
  defaultPersonId,
  defaultUrl,
}: SaveWishlistSheetProps) {
  if (!open) return null;

  const formKey = editingWishlist
    ? `edit-${editingWishlist.id}`
    : `new-${defaultPersonId || ''}-${defaultUrl || ''}`;

  return (
    <SaveWishlistForm
      key={formKey}
      onClose={onClose}
      editingWishlist={editingWishlist}
      defaultPersonId={defaultPersonId}
      defaultUrl={defaultUrl}
    />
  );
}

function SaveWishlistForm({
  onClose,
  editingWishlist,
  defaultPersonId,
  defaultUrl,
}: Omit<SaveWishlistSheetProps, 'open'>) {
  const { people, addPerson, addSavedWishlist, updateSavedWishlist } = useGiftStore();

  const initialPerson = useMemo(
    () =>
      people.find(
        (p) => p.id === (editingWishlist ? editingWishlist.personId : defaultPersonId)
      ) || null,
    [people, editingWishlist, defaultPersonId]
  );

  const [url, setUrl] = useState(() =>
    editingWishlist ? editingWishlist.url : defaultUrl || ''
  );
  const [title, setTitle] = useState(() => (editingWishlist ? editingWishlist.title : ''));
  const [ownerName, setOwnerName] = useState(() =>
    editingWishlist ? editingWishlist.ownerName : initialPerson?.name || ''
  );
  const [personMode, setPersonMode] = useState<'existing' | 'create' | 'unlinked'>(() => {
    if (editingWishlist) {
      return editingWishlist.personId ? 'existing' : 'unlinked';
    }
    if (defaultPersonId) return 'existing';
    return people.length > 0 ? 'existing' : 'create';
  });
  const [selectedPersonId, setSelectedPersonId] = useState<string>(() => {
    if (editingWishlist?.personId) return editingWishlist.personId;
    if (defaultPersonId) return defaultPersonId;
    return people[0]?.id || '';
  });
  const [notes, setNotes] = useState(() => (editingWishlist ? editingWishlist.notes : ''));
  const [fetchingMeta, setFetchingMeta] = useState<boolean>(() =>
    Boolean(!editingWishlist && defaultUrl?.trim())
  );
  const [submitting, setSubmitting] = useState(false);

  const parsedUrl = useMemo(() => parseWishlistUrl(url), [url]);

  const handleFetchMetadata = async (rawTarget?: string, skipInitialBusy = false) => {
    const target = (rawTarget ?? url).trim();
    if (!target) return;
    const parsed = parseWishlistUrl(target);
    if (!parsed.normalizedUrl) return;

    if (!skipInitialBusy) {
      setFetchingMeta(true);
    }
    try {
      if (parsed.type === 'live' && parsed.shareId) {
        const docData = await fetchPublicWishlistOnce(parsed.shareId, parsed.projectKey);
        if (docData) {
          if (!title.trim() && docData.title) setTitle(docData.title);
          if (!ownerName.trim() && docData.ownerName) {
            setOwnerName(docData.ownerName);
            const matched = people.find(
              (p) => p.name.trim().toLowerCase() === docData.ownerName.trim().toLowerCase()
            );
            if (matched && !defaultPersonId) {
              setPersonMode('existing');
              setSelectedPersonId(matched.id);
            }
          }
        }
      } else {
        const res = await fetch(
          `/api/scrape-link?url=${encodeURIComponent(parsed.normalizedUrl)}`
        );
        if (res.ok) {
          const data = (await res.json()) as { title?: string; url?: string };
          if (!title.trim() && data.title) setTitle(data.title);
        }
      }
    } catch {
      // Ignore network/offline errors
    } finally {
      setFetchingMeta(false);
    }
  };

  useEffect(() => {
    if (!editingWishlist && defaultUrl?.trim()) {
      void (async () => {
        await Promise.resolve();
        await handleFetchMetadata(defaultUrl, true);
      })();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim() || submitting) return;

    setSubmitting(true);
    try {
      let resolvedPersonId: string | null = null;
      const cleanOwner = ownerName.trim();

      if (personMode === 'existing' && selectedPersonId) {
        resolvedPersonId = selectedPersonId;
      } else if (personMode === 'create') {
        const nameToCreate = cleanOwner || title.split("'")[0]?.trim() || 'Friend';
        const created = addPerson({
          name: nameToCreate,
          budget: 0,
          relationship: 'Family & Close Circle',
          sizes: {},
          notes: '',
        });
        resolvedPersonId = created.id;
      }

      if (editingWishlist) {
        updateSavedWishlist(editingWishlist.id, {
          url: url.trim(),
          title: title.trim() || editingWishlist.title,
          ownerName:
            cleanOwner ||
            people.find((p) => p.id === resolvedPersonId)?.name ||
            editingWishlist.ownerName,
          personId: resolvedPersonId,
          notes: notes.trim(),
        });
      } else {
        await addSavedWishlist({
          url: url.trim(),
          title: title.trim(),
          ownerName:
            cleanOwner || people.find((p) => p.id === resolvedPersonId)?.name || '',
          personId: resolvedPersonId,
          notes: notes.trim(),
        });
      }
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="m3-sheet"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '540px', display: 'flex', flexDirection: 'column', gap: '14px' }}
      >
        <div className="m3-drag-handle" />

        {/* Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              className="m3-avatar"
              data-shape="clover"
              data-tone="teal"
              style={{ width: '38px', height: '38px', flexShrink: 0 }}
            >
              <Link2 size={18} />
            </div>
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
                Shared With Me
              </span>
              <h2 style={{ fontSize: '19px', fontWeight: 800, marginTop: '1px' }}>
                {editingWishlist ? 'Edit Saved Wishlist' : "Save Someone's Wishlist"}
              </h2>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="m3-btn-icon"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', marginTop: '-4px' }}>
          Paste a Gift Tracker <code>/w/...</code> link for live collaborative claims, or any
          external registry URL (Amazon, Etsy, Target, Google Docs, etc.).
        </p>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
          {/* URL Field */}
          <div
            style={{
              background: 'var(--md-surface-container)',
              padding: '12px',
              borderRadius: '18px',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
              <label className="m3-field-label" htmlFor="saved-wl-url" style={{ marginBottom: 0 }}>
                Wishlist Link *
              </label>
              {url.trim() && (
                <span
                  style={{
                    fontSize: '11px',
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: '999px',
                    background:
                      parsedUrl.type === 'live'
                        ? 'var(--md-success-container)'
                        : 'var(--md-secondary-container)',
                    color:
                      parsedUrl.type === 'live'
                        ? 'var(--md-on-success-container)'
                        : 'var(--md-on-secondary-container)',
                  }}
                >
                  {parsedUrl.type === 'live' ? '● Live Synced Checklist' : 'External Registry Link'}
                </span>
              )}
            </div>

            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                id="saved-wl-url"
                type="text"
                required
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                onPaste={(e) => {
                  const pasted = e.clipboardData.getData('text');
                  if (pasted.trim()) {
                    setTimeout(() => void handleFetchMetadata(pasted.trim()), 60);
                  }
                }}
                placeholder="https://.../w/abc123 or amazon.com/hz/wishlist/..."
                className="m3-input"
                style={{ flex: 1 }}
              />
              <button
                type="button"
                disabled={!url.trim() || fetchingMeta}
                onClick={() => void handleFetchMetadata()}
                className="m3-btn-tonal"
                style={{ flexShrink: 0 }}
              >
                {fetchingMeta ? <Loader2 size={14} /> : <Sparkles size={14} />}
                {fetchingMeta ? 'Reading...' : 'Auto-fill'}
              </button>
            </div>
          </div>

          {/* Title & Owner Name */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '10px' }}>
            <div>
              <label className="m3-field-label" htmlFor="saved-wl-title">
                Wishlist Title
              </label>
              <input
                id="saved-wl-title"
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g., Christmas 2026 Wishlist"
                className="m3-input"
              />
            </div>

            <div>
              <label className="m3-field-label" htmlFor="saved-wl-owner">
                Whose List Is This?
              </label>
              <input
                id="saved-wl-owner"
                type="text"
                value={ownerName}
                onChange={(e) => setOwnerName(e.target.value)}
                placeholder="e.g., Mom, Jordan, Maya"
                className="m3-input"
              />
            </div>
          </div>

          {/* Link to Person in People & Sizes */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <label className="m3-field-label" style={{ marginBottom: 0 }}>
              Link to Person in People &amp; Sizes
            </label>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
              {people.length > 0 && (
                <button
                  type="button"
                  className="m3-chip"
                  data-selected={personMode === 'existing'}
                  onClick={() => setPersonMode('existing')}
                >
                  <Users size={13} /> Existing Person ({people.length})
                </button>
              )}
              <button
                type="button"
                className="m3-chip"
                data-selected={personMode === 'create'}
                onClick={() => setPersonMode('create')}
              >
                <UserPlus size={13} /> Create{' '}
                {ownerName.trim() ? `"${ownerName.trim()}"` : 'New Person'}
              </button>
              <button
                type="button"
                className="m3-chip"
                data-selected={personMode === 'unlinked'}
                onClick={() => setPersonMode('unlinked')}
              >
                Don&rsquo;t Link Yet
              </button>
            </div>

            {personMode === 'existing' && people.length > 0 && (
              <select
                className="m3-select"
                value={selectedPersonId}
                onChange={(e) => {
                  const pid = e.target.value;
                  setSelectedPersonId(pid);
                  const chosen = people.find((p) => p.id === pid);
                  if (chosen && !ownerName.trim()) {
                    setOwnerName(chosen.name);
                  }
                }}
              >
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} {p.relationship ? `(${p.relationship})` : ''}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Optional Personal Notes */}
          <div>
            <label className="m3-field-label" htmlFor="saved-wl-notes">
              Private Notes (Only visible to you)
            </label>
            <input
              id="saved-wl-notes"
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Optional reminder (e.g., Ask sibling before buying top pick)..."
              className="m3-input"
            />
          </div>

          {/* Footer Buttons */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              gap: '8px',
              paddingTop: '4px',
            }}
          >
            {editingWishlist?.url && (
              <a
                href={editingWishlist.url}
                target="_blank"
                rel="noopener noreferrer"
                className="m3-btn-text"
                style={{ marginRight: 'auto', textDecoration: 'none' }}
              >
                <ExternalLink size={14} /> Open URL
              </a>
            )}
            <button type="button" onClick={onClose} className="m3-btn-text">
              Cancel
            </button>
            <button
              type="submit"
              disabled={!url.trim() || submitting}
              className="m3-btn-filled"
            >
              <Check size={16} />
              {editingWishlist ? 'Save Changes' : 'Save Wishlist'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
