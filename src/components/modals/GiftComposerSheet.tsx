'use client';

import React, { useState } from 'react';
import { Link2, Loader2, Sparkles, Trash2, X } from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import type { GiftItem } from '@/lib/types';

export interface GiftComposerDefaults {
  title?: string;
  url?: string;
  store?: string;
  cost?: number | null;
  personId?: string | null;
  eventId?: string | null;
  notes?: string;
}

interface GiftComposerSheetProps {
  open: boolean;
  onClose: () => void;
  editingGift?: GiftItem | null;
  initialDefaults?: GiftComposerDefaults;
}

export function GiftComposerSheet({
  open,
  onClose,
  editingGift,
  initialDefaults,
}: GiftComposerSheetProps) {
  if (!open) return null;

  const formKey = editingGift
    ? `edit-${editingGift.id}`
    : `new-${initialDefaults?.url || ''}-${initialDefaults?.personId || ''}-${initialDefaults?.eventId || ''}`;

  return (
    <GiftComposerForm
      key={formKey}
      onClose={onClose}
      editingGift={editingGift}
      initialDefaults={initialDefaults}
    />
  );
}

function GiftComposerForm({
  onClose,
  editingGift,
  initialDefaults,
}: Omit<GiftComposerSheetProps, 'open'>) {
  const { people, events, addGift, updateGift, deleteGift } = useGiftStore();

  const [title, setTitle] = useState(() =>
    editingGift ? editingGift.title : initialDefaults?.title || ''
  );
  const [personId, setPersonId] = useState<string>(() =>
    editingGift ? editingGift.personId || '' : initialDefaults?.personId ?? ''
  );
  const [destination, setDestination] = useState<string>(() => {
    if (editingGift) {
      if (editingGift.eventId) return editingGift.eventId;
      return editingGift.saveForLater ? 'stockpile' : 'ideas';
    }
    return initialDefaults?.eventId ?? 'ideas';
  });
  const [costStr, setCostStr] = useState(() => {
    if (editingGift) {
      return editingGift.cost > 0 ? String(editingGift.cost) : editingGift.costLabel || '';
    }
    return initialDefaults?.cost && initialDefaults.cost > 0 ? String(initialDefaults.cost) : '';
  });
  const [store, setStore] = useState(() =>
    editingGift ? editingGift.store || '' : initialDefaults?.store || ''
  );
  const [url, setUrl] = useState(() =>
    editingGift ? editingGift.url || '' : initialDefaults?.url || ''
  );
  const [tracking, setTracking] = useState(() => (editingGift ? editingGift.tracking || '' : ''));
  const [notes, setNotes] = useState(() =>
    editingGift ? editingGift.notes || '' : initialDefaults?.notes || ''
  );
  const [bought, setBought] = useState(() =>
    editingGift ? editingGift.bought : Boolean(initialDefaults?.eventId)
  );
  const [received, setReceived] = useState(() => (editingGift ? editingGift.received : false));
  const [wrapped, setWrapped] = useState(() => (editingGift ? editingGift.wrapped : false));
  const [scraping, setScraping] = useState(false);

  const handleAutoFillFromUrl = async (targetUrl?: string) => {
    const linkToFetch = (targetUrl ?? url).trim();
    if (!linkToFetch) return;
    setScraping(true);
    try {
      const res = await fetch(`/api/scrape-link?url=${encodeURIComponent(linkToFetch)}`);
      if (res.ok) {
        const data = (await res.json()) as {
          url: string;
          title: string;
          store: string;
          price: number | null;
        };
        if (data.title && !title) setTitle(data.title);
        if (data.store && !store) setStore(data.store);
        if (data.price && !costStr) setCostStr(String(data.price));
        if (data.url) setUrl(data.url);
      }
    } catch {
      // Ignore network error
    } finally {
      setScraping(false);
    }
  };

  const parseNumericCost = (raw: string): { cost: number; costLabel: string | null } => {
    const trimmed = raw.trim();
    if (!trimmed) return { cost: 0, costLabel: null };
    const rangeMatch = trimmed.match(/^\$?(\d+(?:\.\d+)?)\s*[-–]\s*\$?(\d+(?:\.\d+)?)$/);
    if (rangeMatch) {
      const low = parseFloat(rangeMatch[1]);
      return { cost: low, costLabel: `$${rangeMatch[1]}–$${rangeMatch[2]}` };
    }
    const num = parseFloat(trimmed.replace(/[^0-9.]/g, ''));
    return { cost: Number.isNaN(num) ? 0 : num, costLabel: null };
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;

    const { cost, costLabel } = parseNumericCost(costStr);
    const isIdeas = destination === 'ideas' || destination === 'stockpile';
    const saveForLater = destination === 'stockpile';
    const eventId = isIdeas ? null : destination;

    const payload = {
      title: title.trim(),
      personId: personId || null,
      eventId,
      cost,
      costLabel,
      store: store.trim(),
      url: url.trim(),
      tracking: tracking.trim(),
      notes: notes.trim(),
      bought: saveForLater ? true : bought,
      received,
      wrapped,
      saveForLater,
    };

    if (editingGift) {
      updateGift(editingGift.id, payload);
    } else {
      addGift(payload);
    }
    onClose();
  };

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div className="m3-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="m3-drag-handle" />

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '16px',
          }}
        >
          <h2 style={{ fontSize: '19px', fontWeight: 700 }}>
            {editingGift ? 'Edit Gift Details' : 'Add Gift or Idea'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="m3-btn-icon"
            aria-label="Close sheet"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div
            style={{
              background: 'var(--md-surface-container)',
              padding: '12px',
              borderRadius: '18px',
            }}
          >
            <label className="m3-field-label" htmlFor="gift-url-input">
              Product Link (Optional — paste to auto-fill title & store)
            </label>
            <div style={{ display: 'flex', gap: '8px' }}>
              <input
                id="gift-url-input"
                type="text"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                onPaste={(e) => {
                  const pasted = e.clipboardData.getData('text');
                  if (pasted && /^https?:\/\//i.test(pasted.trim())) {
                    setTimeout(() => void handleAutoFillFromUrl(pasted.trim()), 60);
                  }
                }}
                placeholder="https://amazon.com/... or Etsy / Target URL"
                className="m3-input"
                style={{ flex: 1 }}
              />
              <button
                type="button"
                onClick={() => void handleAutoFillFromUrl()}
                disabled={!url.trim() || scraping}
                className="m3-btn-tonal"
                style={{ flexShrink: 0 }}
              >
                {scraping ? <Loader2 size={15} /> : <Sparkles size={15} />}
                {scraping ? 'Reading...' : 'Auto-fill'}
              </button>
            </div>
          </div>

          <div>
            <label className="m3-field-label" htmlFor="gift-title-input">
              Gift Name *
            </label>
            <input
              id="gift-title-input"
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g., Cotopaxi Water Bottle Bag, Super Mario Wonder..."
              className="m3-input"
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label className="m3-field-label" htmlFor="gift-person-select">
                Recipient
              </label>
              <select
                id="gift-person-select"
                value={personId}
                onChange={(e) => setPersonId(e.target.value)}
                className="m3-select"
              >
                <option value="">Not sure who (General Pool)</option>
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} {p.budget > 0 ? `($${p.budget} budget)` : ''}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="m3-field-label" htmlFor="gift-dest-select">
                Where does this live?
              </label>
              <select
                id="gift-dest-select"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                className="m3-select"
              >
                <option value="ideas">💡 Ideas Backlog</option>
                <option value="stockpile">📦 Bought & Saved for Later</option>
                <optgroup label="Events & Occasions">
                  {events.map((ev) => (
                    <option key={ev.id} value={ev.id}>
                      {ev.emoji} {ev.name}
                    </option>
                  ))}
                </optgroup>
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label className="m3-field-label" htmlFor="gift-cost-input">
                Price / Est. Cost ($)
              </label>
              <input
                id="gift-cost-input"
                type="text"
                inputMode="decimal"
                value={costStr}
                onChange={(e) => setCostStr(e.target.value)}
                placeholder="35 or 30-200"
                className="m3-input"
              />
            </div>

            <div>
              <label className="m3-field-label" htmlFor="gift-store-input">
                Store / From
              </label>
              <input
                id="gift-store-input"
                type="text"
                value={store}
                onChange={(e) => setStore(e.target.value)}
                placeholder="Target, Cotopaxi, Etsy..."
                className="m3-input"
              />
            </div>
          </div>

          <div>
            <label className="m3-field-label" htmlFor="gift-tracking-input">
              Tracking # / Digital Code
            </label>
            <input
              id="gift-tracking-input"
              type="text"
              value={tracking}
              onChange={(e) => setTracking(e.target.value)}
              placeholder="USPS / UPS tracking or gift card PIN..."
              className="m3-input"
            />
          </div>

          <div>
            <label className="m3-field-label" htmlFor="gift-notes-input">
              Notes (color, size reminder, etc.)
            </label>
            <input
              id="gift-notes-input"
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Optional notes..."
              className="m3-input"
            />
          </div>

          <div>
            <span className="m3-field-label">Fulfillment Progress</span>
            <div className="m3-connected-group">
              <button
                type="button"
                className="m3-segment-btn"
                data-kind="bought"
                data-checked={bought}
                onClick={() => {
                  const next = !bought;
                  setBought(next);
                  if (!next) {
                    setReceived(false);
                    setWrapped(false);
                  }
                }}
              >
                {bought ? '✓ Bought' : 'Bought'}
              </button>
              <button
                type="button"
                className="m3-segment-btn"
                data-kind="received"
                data-checked={received}
                onClick={() => {
                  const next = !received;
                  setReceived(next);
                  if (next) setBought(true);
                }}
              >
                {received ? '✓ Arrived' : 'Arrived'}
              </button>
              <button
                type="button"
                className="m3-segment-btn"
                data-kind="wrapped"
                data-checked={wrapped}
                onClick={() => {
                  const next = !wrapped;
                  setWrapped(next);
                  if (next) setBought(true);
                }}
              >
                {wrapped ? '✓ Wrapped' : 'Wrapped'}
              </button>
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '10px',
              marginTop: '6px',
            }}
          >
            {editingGift ? (
              <button
                type="button"
                onClick={() => {
                  deleteGift(editingGift.id);
                  onClose();
                }}
                className="m3-btn-tonal"
                style={{
                  background: 'var(--md-danger-container)',
                  color: 'var(--md-on-danger-container)',
                }}
              >
                <Trash2 size={15} />
                Delete
              </button>
            ) : (
              <span style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
                <Link2 size={13} style={{ display: 'inline', verticalAlign: '-2px' }} /> Syncs across
                devices
              </span>
            )}

            <div style={{ display: 'flex', gap: '8px', marginLeft: 'auto' }}>
              <button type="button" onClick={onClose} className="m3-btn-tonal">
                Cancel
              </button>
              <button type="submit" className="m3-btn-filled">
                {editingGift ? 'Save Changes' : 'Save Gift'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
