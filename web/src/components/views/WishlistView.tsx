'use client';

import React, { useEffect, useMemo, useState } from 'react';
import QRCode from 'qrcode';
import {
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  ExternalLink,
  Gift,
  GripVertical,
  Inbox,
  Link2,
  Lock,
  Pencil,
  Plus,
  QrCode,
  Share2,
  Star,
  StickyNote,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { buildSharedWishlistUrl, computePublishedItemsForLink } from '@/lib/firebase';
import { ShareWishlistSheet } from '@/components/modals/ShareWishlistSheet';
import { SaveWishlistSheet } from '@/components/modals/SaveWishlistSheet';
import { SavedWishlistChecklistPanel } from '@/components/views/SavedWishlistChecklistPanel';
import type { SavedWishlist, SharedWishlistLink, WishItem } from '@/lib/types';

interface WishlistViewProps {
  onOpenNewGift?: (defaults?: {
    personId?: string | null;
    eventId?: string | null;
    url?: string;
  }) => void;
}

export function WishlistView({ onOpenNewGift }: WishlistViewProps) {
  const {
    people,
    wishes,
    sharedLinks,
    savedWishlists,
    wishlistSubTab,
    setWishlistSubTab,
    selectedSavedWishlistId,
    setSelectedSavedWishlistId,
    firebaseUser,
    firebaseConfig,
    addWish,
    updateWish,
    deleteWish,
    reorderWishes,
    deleteSharedWishlistLink,
    deleteSavedWishlist,
    showSnackbar,
  } = useGiftStore();

  // --- My Wishlist State ---
  const [filter, setFilter] = useState<'wanted' | 'received'>('wanted');
  const [newTitle, setNewTitle] = useState('');
  const [newUrl, setNewUrl] = useState('');
  const [newNotes, setNewNotes] = useState('');
  const [showAddDetails, setShowAddDetails] = useState(false);

  const [editingWishId, setEditingWishId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editUrl, setEditUrl] = useState('');
  const [editNotes, setEditNotes] = useState('');

  const [editingReceivedId, setEditingReceivedId] = useState<string | null>(null);
  const [fromInput, setFromInput] = useState('');
  const [whenInput, setWhenInput] = useState('Christmas 2026');

  const [showOrderButtons, setShowOrderButtons] = useState(false);
  const [draggedWishId, setDraggedWishId] = useState<string | null>(null);
  const [dragOverWishId, setDragOverWishId] = useState<string | null>(null);
  const [dropPosition, setDropPosition] = useState<'before' | 'after'>('after');

  // --- Share Outbound Modal State ---
  const [shareModalState, setShareModalState] = useState<{
    open: boolean;
    initialTab?: 'create' | 'manage';
    editingLink?: SharedWishlistLink | null;
  }>({ open: false });

  // --- Outbound Links Inline QR / Copy State ---
  const [copiedOutboundId, setCopiedOutboundId] = useState<string | null>(null);
  const [qrOutboundId, setQrOutboundId] = useState<string | null>(null);
  const [qrOutboundDataUrl, setQrOutboundDataUrl] = useState<string | null>(null);

  // --- Shared With Me (Incoming) Modal & Mobile Accordion State ---
  const [saveWishlistModal, setSaveWishlistModal] = useState<{
    open: boolean;
    editing?: SavedWishlist | null;
  }>({ open: false });
  const [mobileExpandedId, setMobileExpandedId] = useState<string | null>(
    () => selectedSavedWishlistId
  );

  const sortedWishes = useMemo(
    () => [...wishes].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    [wishes]
  );
  const wantedWishes = useMemo(() => sortedWishes.filter((w) => !w.received), [sortedWishes]);
  const receivedWishes = useMemo(() => sortedWishes.filter((w) => w.received), [sortedWishes]);
  const displayed = filter === 'wanted' ? wantedWishes : receivedWishes;

  const peopleMap = useMemo(() => new Map(people.map((p) => [p.id, p])), [people]);

  const activeSavedWishlist = useMemo(() => {
    if (savedWishlists.length === 0) return null;
    return (
      savedWishlists.find((sw) => sw.id === selectedSavedWishlistId) || savedWishlists[0]
    );
  }, [savedWishlists, selectedSavedWishlistId]);

  const activeOutboundQrUrl = useMemo(() => {
    if (!qrOutboundId) return '';
    return buildSharedWishlistUrl(qrOutboundId, firebaseConfig);
  }, [qrOutboundId, firebaseConfig]);

  useEffect(() => {
    if (!activeOutboundQrUrl) return;
    let cancelled = false;
    void QRCode.toDataURL(activeOutboundQrUrl, {
      width: 200,
      margin: 2,
      color: { dark: '#141218', light: '#ffffff' },
    })
      .then((url) => {
        if (!cancelled) setQrOutboundDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setQrOutboundDataUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [activeOutboundQrUrl]);

  const handleAddWish = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    addWish({
      title: newTitle.trim(),
      url: newUrl.trim(),
      notes: newNotes.trim(),
      received: false,
      receivedFrom: '',
      receivedWhen: '',
      priority: 'medium',
    });
    setNewTitle('');
    setNewUrl('');
    setNewNotes('');
    setShowAddDetails(false);
  };

  const handleStartEdit = (wish: WishItem) => {
    setEditingWishId(wish.id);
    setEditTitle(wish.title);
    setEditUrl(wish.url || '');
    setEditNotes(wish.notes || '');
    setEditingReceivedId(null);
  };

  const handleSaveEdit = (e: React.FormEvent, wishId: string) => {
    e.preventDefault();
    if (!editTitle.trim()) return;
    updateWish(wishId, {
      title: editTitle.trim(),
      url: editUrl.trim(),
      notes: editNotes.trim(),
    });
    setEditingWishId(null);
    showSnackbar('Saved changes to wish idea');
  };

  const commitDisplayedOrder = (nextDisplayed: WishItem[]) => {
    const others = filter === 'wanted' ? receivedWishes : wantedWishes;
    const merged =
      filter === 'wanted' ? [...nextDisplayed, ...others] : [...others, ...nextDisplayed];
    reorderWishes(merged.map((w) => w.id));
  };

  const handleDragStart = (e: React.DragEvent, id: string) => {
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', id);
    setDraggedWishId(id);
  };

  const handleDragOver = (e: React.DragEvent, id: string) => {
    if (draggedWishId === id) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    const rect = e.currentTarget.getBoundingClientRect();
    const midY = rect.top + rect.height / 2;
    setDragOverWishId(id);
    setDropPosition(e.clientY < midY ? 'before' : 'after');
  };

  const handleDragLeave = (e: React.DragEvent) => {
    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
    setDragOverWishId(null);
  };

  const handleDrop = (e: React.DragEvent, targetId: string) => {
    e.preventDefault();
    if (!draggedWishId || draggedWishId === targetId) {
      setDraggedWishId(null);
      setDragOverWishId(null);
      return;
    }

    const draggedIdx = displayed.findIndex((w) => w.id === draggedWishId);
    const targetIdx = displayed.findIndex((w) => w.id === targetId);
    if (draggedIdx === -1 || targetIdx === -1) {
      setDraggedWishId(null);
      setDragOverWishId(null);
      return;
    }

    const itemToMove = displayed[draggedIdx];
    const without = displayed.filter((w) => w.id !== draggedWishId);
    let insertIdx = without.findIndex((w) => w.id === targetId);
    if (dropPosition === 'after') insertIdx += 1;
    without.splice(insertIdx, 0, itemToMove);

    commitDisplayedOrder(without);
    setDraggedWishId(null);
    setDragOverWishId(null);
  };

  const handleDragEnd = () => {
    setDraggedWishId(null);
    setDragOverWishId(null);
  };

  const handleMoveStep = (id: string, direction: 'up' | 'down') => {
    const idx = displayed.findIndex((w) => w.id === id);
    if (idx === -1) return;
    const targetIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= displayed.length) return;

    const copy = [...displayed];
    const [moved] = copy.splice(idx, 1);
    copy.splice(targetIdx, 0, moved);
    commitDisplayedOrder(copy);
  };

  const handleCopyOutboundUrl = async (link: SharedWishlistLink) => {
    const url = buildSharedWishlistUrl(link.id, firebaseConfig);
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      setCopiedOutboundId(link.id);
      showSnackbar(`Copied link for "${link.title}"!`);
      setTimeout(() => setCopiedOutboundId(null), 2200);
    }
  };

  const handleShareOutboundUrl = async (link: SharedWishlistLink) => {
    const url = buildSharedWishlistUrl(link.id, firebaseConfig);
    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: link.title,
          text: `🎁 ${link.ownerName ? `${link.ownerName}'s` : 'My'} live wishlist (${link.title}) — claim items here so nobody duplicates gifts:`,
          url,
        });
        return;
      } catch {
        // Fallback to clipboard copy
      }
    }
    await handleCopyOutboundUrl(link);
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '18px',
        maxWidth: wishlistSubTab === 'incoming' ? '1240px' : '980px',
      }}
    >
      <style>{`
        @media (max-width: 759px) {
          .wishlist-incoming-detail-pane { display: none !important; }
        }
        @media (min-width: 760px) {
          .wishlist-incoming-mobile-inline { display: none !important; }
        }
      `}</style>

      {/* 3-Way Segmented Switcher Bar: My Wishlist (Default) | Shared With Me | Links I Shared */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '10px',
        }}
      >
        <div
          className="m3-connected-group"
          role="tablist"
          aria-label="Wishlists Hub Sections"
          style={{ flex: '1 1 340px', maxWidth: '580px' }}
        >
          <button
            type="button"
            role="tab"
            aria-selected={wishlistSubTab === 'mine'}
            className="m3-segment-btn"
            data-kind="bought"
            data-checked={wishlistSubTab === 'mine'}
            onClick={() => setWishlistSubTab('mine')}
          >
            <Star size={14} />
            <span>My Wishlist</span>
            <span style={{ opacity: 0.8, fontSize: '11.5px' }}>({wantedWishes.length})</span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={wishlistSubTab === 'incoming'}
            className="m3-segment-btn"
            data-kind="received"
            data-checked={wishlistSubTab === 'incoming'}
            onClick={() => setWishlistSubTab('incoming')}
          >
            <Inbox size={14} />
            <span>Shared With Me</span>
            <span style={{ opacity: 0.8, fontSize: '11.5px' }}>({savedWishlists.length})</span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={wishlistSubTab === 'outbound'}
            className="m3-segment-btn"
            data-kind="bought"
            data-checked={wishlistSubTab === 'outbound'}
            onClick={() => setWishlistSubTab('outbound')}
          >
            <Link2 size={14} />
            <span>Links I Shared</span>
            <span style={{ opacity: 0.8, fontSize: '11.5px' }}>({sharedLinks.length})</span>
          </button>
        </div>

        {/* Contextual Primary Action */}
        {wishlistSubTab === 'mine' && (
          <button
            type="button"
            onClick={() =>
              setShareModalState({
                open: true,
                initialTab: wantedWishes.length === 0 && sharedLinks.length > 0 ? 'manage' : 'create',
              })
            }
            disabled={wantedWishes.length === 0 && sharedLinks.length === 0}
            className="m3-btn-filled"
            style={{ opacity: wantedWishes.length === 0 && sharedLinks.length === 0 ? 0.6 : 1 }}
          >
            <Share2 size={15} />
            {sharedLinks.length > 0 ? `Share My List (${sharedLinks.length})` : 'Share Wishlist'}
          </button>
        )}

        {wishlistSubTab === 'incoming' && (
          <button
            type="button"
            onClick={() => setSaveWishlistModal({ open: true, editing: null })}
            className="m3-btn-filled"
          >
            <Plus size={16} /> Save a Wishlist
          </button>
        )}

        {wishlistSubTab === 'outbound' && (
          <button
            type="button"
            onClick={() =>
              setShareModalState({ open: true, initialTab: 'create', editingLink: null })
            }
            className="m3-btn-filled"
          >
            <Plus size={16} /> Create Share Link
          </button>
        )}
      </div>

      {/* =====================================================================
         SUB-VIEW 1: MY WISHLIST (DEFAULT)
         ===================================================================== */}
      {wishlistSubTab === 'mine' && (
        <>
          {/* Hero Card */}
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
                Personal Wishlist (Editable by You)
              </span>
              <h2 style={{ fontSize: '22px', fontWeight: 700, marginTop: '2px' }}>
                {wantedWishes.length} Active Wishes • {receivedWishes.length} Received
              </h2>
              <p
                style={{
                  fontSize: '13.5px',
                  color: 'var(--md-on-surface-variant)',
                  marginTop: '2px',
                }}
              >
                Keep ideas for yourself handy and share separate live checklists when family or
                friends ask what you want.
              </p>
            </div>

            {sharedLinks.length > 0 && (
              <button
                type="button"
                onClick={() => setWishlistSubTab('outbound')}
                className="m3-btn-tonal"
              >
                <Link2 size={15} /> Manage {sharedLinks.length} Shared{' '}
                {sharedLinks.length === 1 ? 'Link' : 'Links'} →
              </button>
            )}
          </div>

          {/* Quick Add Wish Form */}
          <form
            onSubmit={handleAddWish}
            className="m3-surface-card"
            style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}
          >
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
              <input
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="Add something to your wishlist (e.g., Switch 2 game, coffee beans)..."
                className="m3-input"
                style={{ flex: '2 1 240px' }}
              />

              <button
                type="button"
                onClick={() => setShowAddDetails((prev) => !prev)}
                className="m3-btn-tonal"
                style={{ height: '40px', fontSize: '12.5px', flexShrink: 0 }}
              >
                <StickyNote size={14} />
                {showAddDetails ? 'Hide Details' : 'Add Details'}
                {showAddDetails ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
              </button>

              <button
                type="submit"
                disabled={!newTitle.trim()}
                className="m3-btn-filled"
                style={{ height: '40px' }}
              >
                <Plus size={16} /> Add Wish
              </button>
            </div>

            {showAddDetails && (
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: '10px',
                  paddingTop: '8px',
                  borderTop: '1px solid var(--md-outline-variant)',
                }}
              >
                <input
                  type="text"
                  value={newUrl}
                  onChange={(e) => setNewUrl(e.target.value)}
                  placeholder="Optional link (e.g. https://...)..."
                  className="m3-input"
                  style={{ flex: '1 1 200px' }}
                />
                <input
                  type="text"
                  value={newNotes}
                  onChange={(e) => setNewNotes(e.target.value)}
                  placeholder="Optional subnotes (size, color, brand, details)..."
                  className="m3-input"
                  style={{ flex: '2 1 260px' }}
                />
              </div>
            )}
          </form>

          {/* Segmented Filter + Reorder */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '8px',
            }}
          >
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

            {displayed.length > 1 && (
              <button
                type="button"
                onClick={() => setShowOrderButtons((prev) => !prev)}
                className={showOrderButtons ? 'm3-btn-filled' : 'm3-btn-tonal'}
                style={{
                  height: '34px',
                  fontSize: '12px',
                  padding: '0 12px',
                  borderRadius: '12px',
                  flexShrink: 0,
                }}
              >
                {showOrderButtons ? 'Done' : 'Reorder'}
              </button>
            )}
          </div>

          {displayed.length > 1 && !showOrderButtons && !editingWishId && (
            <span
              style={{
                fontSize: '11.5px',
                color: 'var(--md-on-surface-variant)',
                paddingLeft: '4px',
                marginTop: '-10px',
              }}
            >
              💡 Drag cards to rearrange your wishlist, or tap <strong>Reorder</strong>.
            </span>
          )}

          {/* Wishes List */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {displayed.length === 0 ? (
              <div
                className="m3-surface-card"
                style={{
                  padding: '32px 20px',
                  textAlign: 'center',
                  color: 'var(--md-on-surface-variant)',
                }}
              >
                <p style={{ fontSize: '14.5px' }}>
                  {filter === 'wanted'
                    ? 'Your wishlist is empty! Add an idea using the form above.'
                    : 'No received gifts logged yet.'}
                </p>
              </div>
            ) : (
              displayed.map((wish, index) => {
                const isEditing = editingWishId === wish.id;
                const isLogging = editingReceivedId === wish.id;
                const isDragging = draggedWishId === wish.id;
                const isOver = dragOverWishId === wish.id;
                const canDrag = !isEditing && displayed.length > 1;

                let dropShadow = 'none';
                if (isOver && !isDragging) {
                  dropShadow =
                    dropPosition === 'before'
                      ? 'inset 0 3px 0 0 var(--md-primary)'
                      : 'inset 0 -3px 0 0 var(--md-primary)';
                }

                return (
                  <div
                    key={wish.id}
                    className="m3-surface-card"
                    draggable={canDrag}
                    onDragStart={(e) => canDrag && handleDragStart(e, wish.id)}
                    onDragOver={(e) => canDrag && handleDragOver(e, wish.id)}
                    onDragLeave={handleDragLeave}
                    onDrop={(e) => canDrag && handleDrop(e, wish.id)}
                    onDragEnd={handleDragEnd}
                    style={{
                      padding: '14px 16px',
                      opacity: isDragging ? 0.35 : 1,
                      boxShadow: dropShadow,
                      transition: 'opacity 160ms ease, box-shadow 160ms ease',
                    }}
                  >
                    {isEditing ? (
                      <form
                        onSubmit={(e) => handleSaveEdit(e, wish.id)}
                        style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            gap: '8px',
                          }}
                        >
                          <span
                            style={{
                              fontSize: '12px',
                              fontWeight: 700,
                              color: 'var(--md-primary)',
                              textTransform: 'uppercase',
                            }}
                          >
                            Edit Wish Idea
                          </span>
                          <button
                            type="button"
                            onClick={() => setEditingWishId(null)}
                            className="m3-btn-icon"
                            style={{ width: '28px', height: '28px' }}
                            aria-label="Cancel edit"
                          >
                            <X size={15} />
                          </button>
                        </div>

                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                          <input
                            type="text"
                            value={editTitle}
                            onChange={(e) => setEditTitle(e.target.value)}
                            placeholder="Wish Title (e.g. Leather boots)"
                            required
                            className="m3-input"
                            style={{ flex: '2 1 220px' }}
                          />
                          <input
                            type="text"
                            value={editUrl}
                            onChange={(e) => setEditUrl(e.target.value)}
                            placeholder="Product URL (optional)"
                            className="m3-input"
                            style={{ flex: '1 1 180px' }}
                          />
                        </div>

                        <div>
                          <textarea
                            rows={2}
                            value={editNotes}
                            onChange={(e) => setEditNotes(e.target.value)}
                            placeholder="Subnotes & specifications (e.g., Size 10.5, dark brown, or waterproof)..."
                            className="m3-textarea"
                            style={{ fontSize: '13px' }}
                          />
                        </div>

                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                          <button
                            type="button"
                            onClick={() => setEditingWishId(null)}
                            className="m3-btn-text"
                            style={{ height: '34px' }}
                          >
                            Cancel
                          </button>
                          <button type="submit" className="m3-btn-filled" style={{ height: '34px' }}>
                            Save Idea
                          </button>
                        </div>
                      </form>
                    ) : (
                      <>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            justifyContent: 'space-between',
                            gap: '12px',
                          }}
                        >
                          {canDrag && (
                            <div
                              title="Drag to reorder"
                              style={{
                                cursor: 'grab',
                                display: 'flex',
                                alignItems: 'center',
                                color: 'var(--md-on-surface-variant)',
                                opacity: 0.55,
                                paddingTop: '3px',
                                flexShrink: 0,
                              }}
                            >
                              <GripVertical size={16} />
                            </div>
                          )}

                          <div style={{ minWidth: 0, flex: 1 }}>
                            <h3
                              style={{
                                fontSize: '15.5px',
                                fontWeight: 700,
                                textDecoration: wish.received ? 'line-through' : 'none',
                                opacity: wish.received ? 0.78 : 1,
                                lineHeight: 1.35,
                              }}
                            >
                              {wish.title}
                            </h3>

                            {wish.notes && (
                              <div
                                style={{
                                  marginTop: '6px',
                                  fontSize: '13px',
                                  lineHeight: '1.45',
                                  color: 'var(--md-on-surface)',
                                  background: 'var(--md-surface-container-low)',
                                  padding: '6px 10px',
                                  borderRadius: '10px',
                                  borderLeft: '3px solid var(--md-primary)',
                                  whiteSpace: 'pre-wrap',
                                }}
                              >
                                {wish.notes}
                              </div>
                            )}

                            <div
                              style={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                gap: '10px',
                                marginTop: '5px',
                                fontSize: '12.5px',
                                color: 'var(--md-on-surface-variant)',
                                alignItems: 'center',
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
                                  <ExternalLink size={12} /> Open Link
                                </a>
                              )}
                            </div>
                          </div>

                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
                            {showOrderButtons && displayed.length > 1 && (
                              <div style={{ display: 'flex', gap: '2px', alignItems: 'center' }}>
                                <button
                                  type="button"
                                  disabled={index === 0}
                                  onClick={() => handleMoveStep(wish.id, 'up')}
                                  className="m3-btn-icon"
                                  style={{
                                    width: '28px',
                                    height: '28px',
                                    opacity: index === 0 ? 0.3 : 0.85,
                                  }}
                                  aria-label="Move Up"
                                >
                                  <ChevronUp size={14} />
                                </button>
                                <button
                                  type="button"
                                  disabled={index === displayed.length - 1}
                                  onClick={() => handleMoveStep(wish.id, 'down')}
                                  className="m3-btn-icon"
                                  style={{
                                    width: '28px',
                                    height: '28px',
                                    opacity: index === displayed.length - 1 ? 0.3 : 0.85,
                                  }}
                                  aria-label="Move Down"
                                >
                                  <ChevronDown size={14} />
                                </button>
                              </div>
                            )}

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
                              onClick={() => handleStartEdit(wish)}
                              className="m3-btn-icon"
                              style={{ width: '34px', height: '34px' }}
                              aria-label="Edit wish idea"
                            >
                              <Pencil size={14} />
                            </button>

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
                      </>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </>
      )}

      {/* =====================================================================
         SUB-VIEW 2: SHARED WITH ME (INCOMING WISHLISTS FROM OTHERS)
         ===================================================================== */}
      {wishlistSubTab === 'incoming' && (
        <>
          <div
            className="m3-surface-card"
            style={{
              padding: '14px 18px',
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '8px',
              fontSize: '13px',
            }}
          >
            <span style={{ color: 'var(--md-on-surface-variant)' }}>
              <strong style={{ color: 'var(--md-on-surface)' }}>
                Read-only wishlists shared with you:
              </strong>{' '}
              Claim items secretly and add gifts to your Occasions tracker without modifying the
              owner&rsquo;s list.
            </span>
            <span style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
              Also linked inside <strong>People &amp; Sizes</strong>
            </span>
          </div>

          {savedWishlists.length === 0 ? (
            <div
              className="m3-hero-card"
              style={{
                textAlign: 'center',
                padding: '36px 24px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '12px',
              }}
            >
              <div
                className="m3-avatar"
                data-shape="clover"
                data-tone="teal"
                style={{ width: '52px', height: '52px' }}
              >
                <Inbox size={22} />
              </div>
              <h2 style={{ fontSize: '20px', fontWeight: 800 }}>
                No Shared Wishlists Saved Yet
              </h2>
              <p
                style={{
                  fontSize: '13.5px',
                  color: 'var(--md-on-surface-variant)',
                  maxWidth: '500px',
                  lineHeight: 1.5,
                }}
              >
                Save a friend or family member&rsquo;s Gift Tracker <code>/w/...</code> link for
                live synced claims, or bookmark external Amazon, Etsy, and Target registries right
                alongside their clothing sizes.
              </p>
              <button
                type="button"
                onClick={() => setSaveWishlistModal({ open: true, editing: null })}
                className="m3-btn-filled"
              >
                <Plus size={16} /> Save Someone&rsquo;s Wishlist
              </button>
            </div>
          ) : (
            <div className="canonical-list-detail">
              {/* LEFT PANE (Directory on Foldable/Desktop, Accordion List on Mobile) */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', minWidth: 0 }}>
                {savedWishlists.map((sw) => {
                  const linkedPerson = sw.personId ? peopleMap.get(sw.personId) : undefined;
                  const selected = activeSavedWishlist?.id === sw.id;
                  const mobileExpanded = mobileExpandedId === sw.id;
                  const displayOwner = sw.ownerName || linkedPerson?.name || 'Shared';

                  return (
                    <div
                      key={sw.id}
                      style={{
                        padding: '14px 16px',
                        borderRadius: selected ? '24px' : '18px',
                        background: selected
                          ? 'var(--md-secondary-container)'
                          : 'var(--md-surface-container)',
                        color: selected
                          ? 'var(--md-on-secondary-container)'
                          : 'var(--md-on-surface)',
                        transition: 'all 200ms ease',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          flexWrap: 'wrap',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: '10px',
                          cursor: 'pointer',
                        }}
                        onClick={() => {
                          setSelectedSavedWishlistId(sw.id);
                          setMobileExpandedId((prev) => (prev === sw.id ? null : sw.id));
                        }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '12px',
                            minWidth: 0,
                            flex: '1 1 200px',
                          }}
                        >
                          <div
                            className="m3-avatar"
                            data-shape={linkedPerson?.avatarShape || 'squircle'}
                            data-tone={linkedPerson?.avatarTone || 'teal'}
                            style={{ flexShrink: 0 }}
                          >
                            {displayOwner.slice(0, 2).toUpperCase()}
                          </div>

                          <div style={{ minWidth: 0, flex: 1 }}>
                            <div
                              style={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                alignItems: 'center',
                                gap: '6px',
                              }}
                            >
                              <span style={{ fontWeight: 700, fontSize: '15px' }}>
                                {displayOwner} &mdash; {sw.title}
                              </span>
                              <span
                                style={{
                                  fontSize: '10.5px',
                                  fontWeight: 700,
                                  padding: '2px 7px',
                                  borderRadius: '999px',
                                  background:
                                    sw.type === 'live'
                                      ? 'var(--md-success-container)'
                                      : 'var(--md-surface-container-high)',
                                  color:
                                    sw.type === 'live'
                                      ? 'var(--md-on-success-container)'
                                      : 'var(--md-on-surface-variant)',
                                }}
                              >
                                {sw.type === 'live' ? '● Live' : 'External'}
                              </span>
                            </div>

                            <div
                              style={{
                                fontSize: '12px',
                                opacity: 0.78,
                                marginTop: '2px',
                                display: 'flex',
                                flexWrap: 'wrap',
                                alignItems: 'center',
                                gap: '6px',
                              }}
                            >
                              {linkedPerson ? (
                                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                                  <Users size={11} /> Linked to {linkedPerson.name}
                                </span>
                              ) : (
                                <span>Not linked to a person yet</span>
                              )}
                            </div>
                          </div>
                        </div>

                        <div
                          style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}
                          onClick={(e) => e.stopPropagation()}
                        >
                          {/* Mobile Toggle Button */}
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedSavedWishlistId(sw.id);
                              setMobileExpandedId((prev) => (prev === sw.id ? null : sw.id));
                            }}
                            className="m3-btn-tonal wishlist-incoming-mobile-inline"
                            style={{ height: '32px', fontSize: '12px', padding: '0 10px' }}
                          >
                            {mobileExpanded
                              ? 'Hide'
                              : sw.type === 'live'
                              ? 'Browse & Claim'
                              : 'Details'}
                          </button>

                          <button
                            type="button"
                            onClick={() => setSaveWishlistModal({ open: true, editing: sw })}
                            className="m3-btn-icon"
                            style={{ width: '32px', height: '32px' }}
                            title="Edit saved wishlist"
                            aria-label="Edit saved wishlist"
                          >
                            <Pencil size={14} />
                          </button>

                          <button
                            type="button"
                            onClick={() => deleteSavedWishlist(sw.id)}
                            className="m3-btn-icon"
                            style={{ width: '32px', height: '32px' }}
                            title="Remove saved wishlist"
                            aria-label="Remove saved wishlist"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>

                      {/* Compact Mobile Inline Accordion */}
                      {mobileExpanded && (
                        <div className="wishlist-incoming-mobile-inline">
                          <SavedWishlistChecklistPanel
                            savedWishlist={sw}
                            onEdit={(target) => setSaveWishlistModal({ open: true, editing: target })}
                            onDelete={deleteSavedWishlist}
                            onLogExternalGift={(pid, extUrl) =>
                              onOpenNewGift?.({ personId: pid, url: extUrl })
                            }
                            compactInline
                          />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* RIGHT PANE (2-Pane Live Checklist Detail on Foldable & Desktop) */}
              {activeSavedWishlist && (
                <div className="wishlist-incoming-detail-pane" style={{ minWidth: 0 }}>
                  <SavedWishlistChecklistPanel
                    key={activeSavedWishlist.id}
                    savedWishlist={activeSavedWishlist}
                    onEdit={(target) => setSaveWishlistModal({ open: true, editing: target })}
                    onDelete={deleteSavedWishlist}
                    onLogExternalGift={(pid, extUrl) =>
                      onOpenNewGift?.({ personId: pid, url: extUrl })
                    }
                  />
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* =====================================================================
         SUB-VIEW 3: LINKS I SHARED (OUTBOUND WISHLIST LINKS)
         ===================================================================== */}
      {wishlistSubTab === 'outbound' && (
        <>
          <div
            className="m3-surface-card"
            style={{
              padding: '14px 18px',
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '8px',
              fontSize: '13px',
            }}
          >
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                color: 'var(--md-on-surface-variant)',
              }}
            >
              <Lock size={15} style={{ color: 'var(--md-primary)', flexShrink: 0 }} />
              <span>
                <strong style={{ color: 'var(--md-on-surface)' }}>
                  Outbound links you shared with others:
                </strong>{' '}
                Share separate subsets of your wishlist with Family, Friends, or Secret Santa.
                Firestore security rules strictly hide claim statuses from you.
              </span>
            </span>
          </div>

          {!firebaseUser && (
            <div
              className="m3-surface-card-low"
              style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)' }}
            >
              💡 Sign in to Cloud Sync in Settings to publish live collaborative group links, or tap{' '}
              <strong>Create Share Link</strong> above to copy/share a plain-text wishlist.
            </div>
          )}

          {sharedLinks.length === 0 ? (
            <div
              className="m3-hero-card"
              style={{
                textAlign: 'center',
                padding: '36px 24px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '12px',
              }}
            >
              <div
                className="m3-avatar"
                data-shape="squircle"
                data-tone="terracotta"
                style={{ width: '52px', height: '52px' }}
              >
                <Share2 size={22} />
              </div>
              <h2 style={{ fontSize: '20px', fontWeight: 800 }}>
                No Outbound Wishlist Links Yet
              </h2>
              <p
                style={{
                  fontSize: '13.5px',
                  color: 'var(--md-on-surface-variant)',
                  maxWidth: '480px',
                  lineHeight: 1.5,
                }}
              >
                Create an unlisted checklist link for Family or Friends so they can claim items and
                coordinate gifts without spoiling your surprise.
              </p>
              <button
                type="button"
                onClick={() =>
                  setShareModalState({ open: true, initialTab: 'create', editingLink: null })
                }
                className="m3-btn-filled"
              >
                <Plus size={16} /> Create First Share Link
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {sharedLinks.map((link) => {
                const publishedCount = computePublishedItemsForLink(link, sortedWishes).length;
                const shareUrl = buildSharedWishlistUrl(link.id, firebaseConfig);
                const isShowingQr = qrOutboundId === link.id;
                const isCopied = copiedOutboundId === link.id;

                return (
                  <div
                    key={link.id}
                    className="m3-surface-card"
                    style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: '12px',
                      }}
                    >
                      <div style={{ minWidth: 0, flex: '1 1 240px' }}>
                        <div
                          style={{
                            display: 'flex',
                            flexWrap: 'wrap',
                            alignItems: 'center',
                            gap: '8px',
                          }}
                        >
                          <h3 style={{ fontSize: '16.5px', fontWeight: 800 }}>{link.title}</h3>
                          <span
                            style={{
                              fontSize: '11px',
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: '999px',
                              background: 'var(--md-secondary-container)',
                              color: 'var(--md-on-secondary-container)',
                            }}
                          >
                            {link.includeAllActive
                              ? `All Active Wishes (${publishedCount})`
                              : `${publishedCount} Selected ${
                                  publishedCount === 1 ? 'Wish' : 'Wishes'
                                }`}
                          </span>
                        </div>

                        <div
                          style={{
                            fontSize: '12px',
                            color: 'var(--md-on-surface-variant)',
                            marginTop: '4px',
                            wordBreak: 'break-all',
                          }}
                        >
                          {shareUrl}
                        </div>
                      </div>

                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                        <button
                          type="button"
                          onClick={() => void handleCopyOutboundUrl(link)}
                          className="m3-btn-filled"
                          style={{ height: '34px', fontSize: '12.5px' }}
                        >
                          {isCopied ? <Check size={14} /> : <Copy size={14} />}
                          {isCopied ? 'Copied!' : 'Copy Link'}
                        </button>

                        <button
                          type="button"
                          onClick={() => void handleShareOutboundUrl(link)}
                          className="m3-btn-tonal"
                          style={{ height: '34px', fontSize: '12.5px' }}
                        >
                          <Share2 size={14} /> Share
                        </button>

                        <button
                          type="button"
                          onClick={() =>
                            setQrOutboundId((prev) => (prev === link.id ? null : link.id))
                          }
                          className="m3-btn-tonal"
                          style={{ height: '34px', fontSize: '12.5px' }}
                        >
                          <QrCode size={14} /> {isShowingQr ? 'Hide QR' : 'QR'}
                        </button>

                        <a
                          href={`/w/${encodeURIComponent(link.id)}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="m3-btn-tonal"
                          style={{ height: '34px', fontSize: '12.5px', textDecoration: 'none' }}
                        >
                          <ExternalLink size={14} /> Preview
                        </a>

                        <button
                          type="button"
                          onClick={() =>
                            setShareModalState({
                              open: true,
                              initialTab: 'create',
                              editingLink: link,
                            })
                          }
                          className="m3-btn-icon"
                          style={{ width: '34px', height: '34px' }}
                          title="Edit link items & settings"
                          aria-label="Edit link"
                        >
                          <Pencil size={14} />
                        </button>

                        <button
                          type="button"
                          onClick={() => {
                            if (
                              window.confirm(
                                `Revoke "${link.title}"? People with this link will no longer be able to view or claim items on it.`
                              )
                            ) {
                              void deleteSharedWishlistLink(link.id);
                            }
                          }}
                          className="m3-btn-icon"
                          style={{ width: '34px', height: '34px' }}
                          title="Revoke link"
                          aria-label="Revoke link"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>

                    {isShowingQr && qrOutboundDataUrl && (
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'center',
                          padding: '12px',
                          background: 'var(--md-surface-container-low)',
                          borderRadius: '16px',
                        }}
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={qrOutboundDataUrl}
                          alt={`QR code for ${link.title}`}
                          width={176}
                          height={176}
                          style={{ borderRadius: '12px', background: '#fff', padding: '6px' }}
                        />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      {/* Share Personal Wishlist Modal */}
      {shareModalState.open && (
        <ShareWishlistSheet
          wishes={wantedWishes}
          initialTab={shareModalState.initialTab}
          initialEditingLink={shareModalState.editingLink}
          onClose={() => setShareModalState({ open: false })}
        />
      )}

      {/* Save / Edit Incoming Wishlist Modal */}
      <SaveWishlistSheet
        open={saveWishlistModal.open}
        editingWishlist={saveWishlistModal.editing}
        onClose={() => setSaveWishlistModal({ open: false })}
      />
    </div>
  );
}
