'use client';

import React, { useMemo, useState } from 'react';
import { Check, ExternalLink, Gift, Sparkles, UserPlus, Users, X } from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import type { GiftItem, PublishedWishItem } from '@/lib/types';
import { claimPublicWishItem, initPublicViewerServices, setWishResearching } from '@/lib/firebase';

interface AddToTrackerSheetProps {
  open: boolean;
  onClose: () => void;
  wish: PublishedWishItem;
  wishlistOwnerName?: string;
  shareId: string;
  fallbackKey?: string | null;
  giverName: string;
  onGiverNameChange: (name: string) => void;
  claimerToken: string;
  onAddedToTracker: (gift: GiftItem, status: 'reserved' | 'researching') => void;
}

export function AddToTrackerSheet({
  open,
  onClose,
  wish,
  wishlistOwnerName,
  shareId,
  fallbackKey,
  giverName,
  onGiverNameChange,
  claimerToken,
  onAddedToTracker,
}: AddToTrackerSheetProps) {
  const { people, events, addPerson, addGift, showSnackbar } = useGiftStore();

  const ownerNameClean = (wishlistOwnerName || '').trim() || 'Wishlist Owner';

  // Check if owner already exists in people
  const matchedPerson = useMemo(() => {
    return people.find(
      (p) => p.name.trim().toLowerCase() === ownerNameClean.toLowerCase()
    );
  }, [people, ownerNameClean]);

  const [mode, setMode] = useState<'create-owner' | 'pick-existing' | 'unassigned'>(() => {
    if (matchedPerson) return 'pick-existing';
    return 'create-owner';
  });

  const [selectedPersonId, setSelectedPersonId] = useState<string>(() => {
    return matchedPerson?.id || (people[0]?.id ?? '');
  });

  const [selectedEventId, setSelectedEventId] = useState<string>(() => {
    const active = events.find((e) => e.isActive && !e.isArchived) || events[0];
    return active?.id || 'ideas';
  });

  const [claimImmediately, setClaimImmediately] = useState<boolean>(true);
  const [giverNameInput, setGiverNameInput] = useState<string>(giverName);
  const [submitting, setSubmitting] = useState<boolean>(false);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;

    const chosenName = (giverNameInput || giverName).trim();
    if (!chosenName) {
      alert('Please enter your name so others on the wishlist know who is tracking/claiming.');
      return;
    }
    if (chosenName !== giverName) {
      onGiverNameChange(chosenName);
    }

    setSubmitting(true);

    try {
      // 1. Resolve Target Person
      let finalPersonId: string | null = null;
      if (mode === 'create-owner') {
        const created = addPerson({
          name: ownerNameClean,
          budget: 0,
          relationship: 'Friend or Family',
          sizes: {},
          notes: `Added from shared wishlist`,
        });
        finalPersonId = created.id;
      } else if (mode === 'pick-existing') {
        finalPersonId = selectedPersonId || null;
      } else {
        finalPersonId = null;
      }

      // 2. Resolve Event
      const finalEventId = selectedEventId === 'ideas' ? null : selectedEventId;

      // 3. Update Shared Wishlist Claim / Research Status
      const services = initPublicViewerServices(fallbackKey);
      const syncStatus = claimImmediately ? 'reserved' : 'researching';

      if (services) {
        if (claimImmediately) {
          const res = await claimPublicWishItem(services.db, shareId, {
            wishId: wish.id,
            claimedBy: chosenName,
            claimerToken,
            purchased: false,
            status: 'reserved',
            researchers: [chosenName],
            researcherTokens: [claimerToken],
            claimedAt: new Date().toISOString(),
          });
          if (!res.ok) {
            showSnackbar(`Note: ${res.claimedBy} just claimed this, but it was saved to your tracker.`);
          }
        } else {
          await setWishResearching(
            services.db,
            shareId,
            wish.id,
            chosenName,
            claimerToken
          );
        }
      }

      // 4. Create Gift in Tracker
      const newGift = addGift({
        title: wish.title,
        personId: finalPersonId,
        eventId: finalEventId,
        cost: 0,
        store: '',
        url: wish.url || '',
        tracking: '',
        notes: wish.notes || '',
        bought: false,
        received: false,
        wrapped: false,
        saveForLater: false,
        sourceWishlistShareId: shareId,
        sourceWishId: wish.id,
        sourceWishlistKey: fallbackKey || undefined,
        sourceSyncStatus: syncStatus,
      });

      onAddedToTracker(newGift, syncStatus);
      onClose();
    } catch {
      showSnackbar('Could not save gift. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="m3-sheet"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '520px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div className="m3-drag-handle" />

        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              className="m3-avatar"
              data-shape="clover"
              data-tone="sage"
              style={{ width: '36px', height: '36px' }}
            >
              <Sparkles size={18} />
            </div>
            <div>
              <h2 style={{ fontSize: '18px', fontWeight: 800 }}>Add to Your Gift Tracker</h2>
              <p style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
                Track this wish in your personal planning & budget
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="m3-btn-icon" aria-label="Close">
            <X size={18} />
          </button>
        </div>

        {/* Wish preview card */}
        <div
          style={{
            background: 'var(--md-surface-container-high)',
            padding: '12px 14px',
            borderRadius: '16px',
            border: '1px solid var(--md-outline-variant)',
          }}
        >
          <div style={{ fontWeight: 700, fontSize: '15px' }}>{wish.title}</div>
          {wish.notes && (
            <div
              style={{
                fontSize: '12.5px',
                color: 'var(--md-on-surface-variant)',
                marginTop: '4px',
                whiteSpace: 'pre-wrap',
              }}
            >
              {wish.notes}
            </div>
          )}
          {wish.url && (
            <a
              href={wish.url}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                fontSize: '12px',
                color: 'var(--md-primary)',
                fontWeight: 600,
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                marginTop: '6px',
              }}
            >
              <ExternalLink size={12} /> {wish.url}
            </a>
          )}
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {/* Recipient / Person Selection */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '13px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Users size={15} /> Who is this gift for?
            </label>

            {matchedPerson ? (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: 'var(--md-surface-container-low)',
                  padding: '10px 14px',
                  borderRadius: '12px',
                  border: '1px solid var(--md-primary)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <div
                    className="m3-avatar"
                    data-shape={matchedPerson.avatarShape}
                    data-tone={matchedPerson.avatarTone}
                    style={{ width: '32px', height: '32px', fontSize: '12px' }}
                  >
                    {matchedPerson.name.slice(0, 1)}
                  </div>
                  <div>
                    <strong style={{ fontSize: '14px' }}>{matchedPerson.name}</strong>
                    <div style={{ fontSize: '11px', color: 'var(--md-on-surface-variant)' }}>
                      Matched recipient in your tracker
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setMode(mode === 'pick-existing' ? 'unassigned' : 'pick-existing')}
                  className="m3-btn-text"
                  style={{ fontSize: '12px', height: '30px' }}
                >
                  Change
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <div
                  style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '6px',
                  }}
                >
                  <button
                    type="button"
                    className="m3-chip"
                    data-selected={mode === 'create-owner'}
                    onClick={() => setMode('create-owner')}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                  >
                    <UserPlus size={13} /> Create recipient &ldquo;{ownerNameClean}&rdquo;
                  </button>
                  {people.length > 0 && (
                    <button
                      type="button"
                      className="m3-chip"
                      data-selected={mode === 'pick-existing'}
                      onClick={() => setMode('pick-existing')}
                    >
                      Choose from your People ({people.length})
                    </button>
                  )}
                  <button
                    type="button"
                    className="m3-chip"
                    data-selected={mode === 'unassigned'}
                    onClick={() => setMode('unassigned')}
                  >
                    Idea Pool (No person yet)
                  </button>
                </div>

                {mode === 'pick-existing' && (
                  <select
                    className="m3-select"
                    value={selectedPersonId}
                    onChange={(e) => setSelectedPersonId(e.target.value)}
                    style={{ marginTop: '4px' }}
                  >
                    {people.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} ({p.relationship || 'Friend/Family'})
                      </option>
                    ))}
                  </select>
                )}
              </div>
            )}
          </div>

          {/* Occasion / Event Selection */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ fontSize: '13px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Gift size={15} /> Which occasion or event?
            </label>
            <select
              className="m3-select"
              value={selectedEventId}
              onChange={(e) => setSelectedEventId(e.target.value)}
            >
              {events
                .filter((e) => !e.isArchived)
                .map((ev) => (
                  <option key={ev.id} value={ev.id}>
                    {ev.emoji} {ev.name} ({ev.year})
                  </option>
                ))}
              <option value="ideas">💡 Ideas Backlog (No occasion / Stockpile)</option>
            </select>
          </div>

          {/* Wishlist Sync Behavior Card */}
          <div
            style={{
              background: 'var(--md-surface-container-low)',
              borderRadius: '14px',
              padding: '12px',
              border: '1px solid var(--md-outline-variant)',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
            }}
          >
            <label
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: '10px',
                cursor: 'pointer',
                userSelect: 'none',
              }}
            >
              <input
                type="checkbox"
                checked={claimImmediately}
                onChange={(e) => setClaimImmediately(e.target.checked)}
                style={{ width: '18px', height: '18px', marginTop: '2px', accentColor: 'var(--md-primary)' }}
              />
              <div>
                <strong style={{ fontSize: '13.5px', display: 'block' }}>
                  {claimImmediately ? 'Claim on wishlist now (Reserved)' : 'Mark as Researching on wishlist'}
                </strong>
                <p style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)', marginTop: '2px' }}>
                  {claimImmediately
                    ? 'Reserves this gift so other gift-givers know you are getting it and won’t buy duplicates.'
                    : 'Shows others you are investigating/researching it, but leaves it open until you mark it Bought in your tracker.'}
                </p>
              </div>
            </label>
          </div>

          {/* Giver Name Input (if not previously saved) */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <label style={{ fontSize: '12.5px', fontWeight: 700 }}>
              Your Name (visible to other gift-givers)
            </label>
            <input
              type="text"
              required
              value={giverNameInput}
              onChange={(e) => setGiverNameInput(e.target.value)}
              placeholder="e.g. Aunt Sarah, Alex..."
              className="m3-input"
              style={{ height: '38px', fontSize: '13px' }}
            />
          </div>

          {/* Submit Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '6px' }}>
            <button type="button" onClick={onClose} className="m3-btn-text" disabled={submitting}>
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !giverNameInput.trim()}
              className="m3-btn-filled"
              style={{ height: '40px' }}
            >
              <Check size={16} /> Add to My Tracker
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
