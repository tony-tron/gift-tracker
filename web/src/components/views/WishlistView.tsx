'use client';

import React, { useMemo, useState } from 'react';
import {
  Check,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  Gift,
  GripVertical,
  Pencil,
  Plus,
  Share2,
  StickyNote,
  Trash2,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { ShareWishlistSheet } from '@/components/modals/ShareWishlistSheet';
import type { WishItem } from '@/lib/types';

export function WishlistView() {
  const { wishes, addWish, updateWish, deleteWish, reorderWishes, showSnackbar } = useGiftStore();

  const [filter, setFilter] = useState<'wanted' | 'received'>('wanted');
  const [newTitle, setNewTitle] = useState('');
  const [newUrl, setNewUrl] = useState('');
  const [newNotes, setNewNotes] = useState('');
  const [showAddDetails, setShowAddDetails] = useState(false);

  // Edit wish state
  const [editingWishId, setEditingWishId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editUrl, setEditUrl] = useState('');
  const [editNotes, setEditNotes] = useState('');

  // Received logging state
  const [editingReceivedId, setEditingReceivedId] = useState<string | null>(null);
  const [fromInput, setFromInput] = useState('');
  const [whenInput, setWhenInput] = useState('Christmas 2026');

  // Share Wishlist selection sheet
  const [isShareSheetOpen, setIsShareSheetOpen] = useState(false);

  // Reorder state
  const [showOrderButtons, setShowOrderButtons] = useState(false);
  const [draggedWishId, setDraggedWishId] = useState<string | null>(null);
  const [dragOverWishId, setDragOverWishId] = useState<string | null>(null);
  const [dropPosition, setDropPosition] = useState<'before' | 'after'>('after');

  const sortedWishes = useMemo(
    () => [...wishes].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    [wishes]
  );
  const wantedWishes = useMemo(() => sortedWishes.filter((w) => !w.received), [sortedWishes]);
  const receivedWishes = useMemo(() => sortedWishes.filter((w) => w.received), [sortedWishes]);

  const displayed = filter === 'wanted' ? wantedWishes : receivedWishes;

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

  const handleCancelEdit = () => {
    setEditingWishId(null);
  };

  const commitDisplayedOrder = (nextDisplayed: WishItem[]) => {
    const others = filter === 'wanted' ? receivedWishes : wantedWishes;
    const merged = filter === 'wanted' ? [...nextDisplayed, ...others] : [...others, ...nextDisplayed];
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
    const pos = e.clientY < midY ? 'before' : 'after';
    setDragOverWishId(id);
    setDropPosition(pos);
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
    if (dropPosition === 'after') {
      insertIdx += 1;
    }
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '18px', maxWidth: '920px' }}>
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
            Personal Wishlist (&ldquo;Me&rdquo; Tab)
          </span>
          <h2 style={{ fontSize: '22px', fontWeight: 700, marginTop: '2px' }}>
            {wantedWishes.length} Active Wishes • {receivedWishes.length} Received
          </h2>
          <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', marginTop: '2px' }}>
            Keep ideas for yourself handy and share a selected or full list when family asks what you want.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setIsShareSheetOpen(true)}
          disabled={wantedWishes.length === 0}
          className="m3-btn-filled"
          style={{ opacity: wantedWishes.length === 0 ? 0.6 : 1 }}
        >
          <Share2 size={15} /> Share Wishlist
        </button>
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

          <button type="submit" disabled={!newTitle.trim()} className="m3-btn-filled" style={{ height: '40px' }}>
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
                  /* Edit Wish Inline Form */
                  <form
                    onSubmit={(e) => handleSaveEdit(e, wish.id)}
                    style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                      <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--md-primary)', textTransform: 'uppercase' }}>
                        Edit Wish Idea
                      </span>
                      <button
                        type="button"
                        onClick={handleCancelEdit}
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
                      <button type="button" onClick={handleCancelEdit} className="m3-btn-text" style={{ height: '34px' }}>
                        Cancel
                      </button>
                      <button type="submit" className="m3-btn-filled" style={{ height: '34px' }}>
                        Save Idea
                      </button>
                    </div>
                  </form>
                ) : (
                  /* Normal Wish Row */
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

                        {/* Subnotes display */}
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
                              title="Move Up"
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
                              title="Move Down"
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
                          title="Edit wish idea & subnotes"
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

      {/* Share Wishlist Selection Sheet */}
      {isShareSheetOpen && (
        <ShareWishlistSheet
          wishes={wantedWishes}
          onClose={() => setIsShareSheetOpen(false)}
        />
      )}
    </div>
  );
}
