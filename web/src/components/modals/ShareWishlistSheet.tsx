'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import QRCode from 'qrcode';
import {
  Check,
  CheckSquare,
  Copy,
  ExternalLink,
  Link2,
  Pencil,
  Plus,
  QrCode,
  Share2,
  Sparkles,
  Square,
  Trash2,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import {
  buildSharedWishlistUrl,
  computePublishedItemsForLink,
} from '@/lib/firebase';
import type { SharedWishlistLink, WishItem } from '@/lib/types';

interface ShareWishlistSheetProps {
  wishes: WishItem[];
  onClose: () => void;
  initialTab?: 'create' | 'manage';
  initialEditingLink?: SharedWishlistLink | null;
}

const OWNER_NAME_PREF_KEY = 'gift_tracker_owner_display_name_v1';

export function ShareWishlistSheet({
  wishes,
  onClose,
  initialTab,
  initialEditingLink,
}: ShareWishlistSheetProps) {
  const router = useRouter();
  const {
    sharedLinks,
    firebaseUser,
    firebaseConfig,
    createSharedWishlistLink,
    updateSharedWishlistLink,
    deleteSharedWishlistLink,
    showSnackbar,
  } = useGiftStore();

  const [tab, setTab] = useState<'create' | 'manage'>(() => {
    if (initialEditingLink) return 'create';
    if (initialTab) return initialTab;
    return wishes.length === 0 && sharedLinks.length > 0 ? 'manage' : 'create';
  });

  // Create / Edit Group Link state
  const [editingLinkId, setEditingLinkId] = useState<string | null>(
    () => initialEditingLink?.id ?? null
  );
  const [groupTitle, setGroupTitle] = useState(
    () => initialEditingLink?.title || 'Family & Friends Wishlist'
  );
  const [ownerName, setOwnerName] = useState<string>(() => {
    if (initialEditingLink?.ownerName) return initialEditingLink.ownerName;
    if (typeof window !== 'undefined') {
      const saved = window.localStorage.getItem(OWNER_NAME_PREF_KEY);
      if (saved) return saved;
    }
    return firebaseUser?.displayName?.split(' ')[0] || '';
  });
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => {
    if (initialEditingLink) {
      return initialEditingLink.includeAllActive
        ? new Set(wishes.map((w) => w.id))
        : new Set(initialEditingLink.wishIds.filter((id) => wishes.some((w) => w.id === id)));
    }
    return new Set(wishes.map((w) => w.id));
  });
  const [includeAllActive, setIncludeAllActive] = useState(
    () => initialEditingLink?.includeAllActive ?? false
  );
  const [includeUrls, setIncludeUrls] = useState(
    () => initialEditingLink?.includeUrls ?? true
  );
  const [includeNotes, setIncludeNotes] = useState(
    () => initialEditingLink?.includeNotes ?? true
  );
  const [showPlainTextPreview, setShowPlainTextPreview] = useState(false);
  const [savingLink, setSavingLink] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // QR expansion in Manage tab
  const [qrLinkId, setQrLinkId] = useState<string | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);

  const allSelected = selectedIds.size === wishes.length && wishes.length > 0;

  const activeQrUrl = useMemo(() => {
    if (!qrLinkId) return '';
    return buildSharedWishlistUrl(qrLinkId, firebaseConfig);
  }, [qrLinkId, firebaseConfig]);

  useEffect(() => {
    if (!activeQrUrl) return;
    let cancelled = false;
    void QRCode.toDataURL(activeQrUrl, {
      width: 200,
      margin: 2,
      color: { dark: '#141218', light: '#ffffff' },
    })
      .then((url) => {
        if (!cancelled) setQrDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setQrDataUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [activeQrUrl]);

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set());
      setIncludeAllActive(false);
    } else {
      setSelectedIds(new Set(wishes.map((w) => w.id)));
    }
  };

  const toggleWish = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
        setIncludeAllActive(false);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const formattedShareText = useMemo(() => {
    const selectedWishes = wishes.filter((w) => includeAllActive || selectedIds.has(w.id));
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

    const heading = groupTitle.trim() || 'My Gift Wishlist';
    return `🎁 ${heading} (${selectedWishes.length} item${selectedWishes.length === 1 ? '' : 's'}):\n\n${lines.join('\n\n')}`;
  }, [wishes, selectedIds, includeAllActive, includeUrls, includeNotes, groupTitle]);

  const resetCreateForm = () => {
    setEditingLinkId(null);
    setGroupTitle('Family & Friends Wishlist');
    setSelectedIds(new Set(wishes.map((w) => w.id)));
    setIncludeAllActive(false);
    setIncludeUrls(true);
    setIncludeNotes(true);
  };

  const handleStartEditLink = (link: SharedWishlistLink) => {
    setEditingLinkId(link.id);
    setGroupTitle(link.title);
    setOwnerName(link.ownerName || ownerName);
    setSelectedIds(
      link.includeAllActive
        ? new Set(wishes.map((w) => w.id))
        : new Set(link.wishIds.filter((id) => wishes.some((w) => w.id === id)))
    );
    setIncludeAllActive(link.includeAllActive);
    setIncludeNotes(link.includeNotes);
    setIncludeUrls(link.includeUrls);
    setTab('create');
  };

  const handleSaveLiveLink = async () => {
    if (savingLink) return;
    if (typeof window !== 'undefined' && ownerName.trim()) {
      window.localStorage.setItem(OWNER_NAME_PREF_KEY, ownerName.trim());
    }

    setSavingLink(true);
    try {
      const chosenIds = includeAllActive
        ? wishes.map((w) => w.id)
        : Array.from(selectedIds);

      if (editingLinkId) {
        await updateSharedWishlistLink(editingLinkId, {
          title: groupTitle.trim() || 'Gift Wishlist',
          ownerName: ownerName.trim(),
          wishIds: chosenIds,
          includeAllActive,
          includeNotes,
          includeUrls,
        });
        setEditingLinkId(null);
        setTab('manage');
        return;
      }

      const created = await createSharedWishlistLink({
        title: groupTitle.trim() || 'Gift Wishlist',
        ownerName: ownerName.trim(),
        wishIds: chosenIds,
        includeAllActive,
        includeNotes,
        includeUrls,
      });

      if (created) {
        const shareUrl = buildSharedWishlistUrl(created.id, firebaseConfig);
        if (typeof navigator !== 'undefined' && navigator.clipboard) {
          await navigator.clipboard.writeText(shareUrl).catch(() => {});
          setCopiedId(created.id);
          setTimeout(() => setCopiedId(null), 2400);
        }
        setQrLinkId(created.id);
        setTab('manage');
      }
    } finally {
      setSavingLink(false);
    }
  };

  const handleCopyUrl = async (link: SharedWishlistLink) => {
    const url = buildSharedWishlistUrl(link.id, firebaseConfig);
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      setCopiedId(link.id);
      showSnackbar(`Copied link for "${link.title}"!`);
      setTimeout(() => setCopiedId(null), 2200);
    }
  };

  const handleNativeShareUrl = async (link: SharedWishlistLink) => {
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
        // Fallback to copy
      }
    }
    await handleCopyUrl(link);
  };

  const handleCopyPlainText = async () => {
    if (!formattedShareText) return;
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      await navigator.clipboard.writeText(formattedShareText);
      setCopiedId('plain-text');
      showSnackbar('Copied plain-text wishlist to clipboard!');
      setTimeout(() => setCopiedId(null), 2200);
    }
  };

  const handleSharePlainText = async () => {
    if (!formattedShareText) return;
    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: groupTitle.trim() || 'My Gift Wishlist',
          text: formattedShareText,
        });
        return;
      } catch {
        // Fallback to copy
      }
    }
    await handleCopyPlainText();
  };

  const canCreateOrSave = includeAllActive || selectedIds.size > 0;

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="m3-sheet"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '620px', display: 'flex', flexDirection: 'column', gap: '14px' }}
      >
        <div className="m3-drag-handle" />

        {/* Sheet Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
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
              Collaborative Wishlist Sharing
            </span>
            <h2 style={{ fontSize: '20px', fontWeight: 800, marginTop: '2px' }}>
              {editingLinkId ? 'Edit Group Wishlist Link' : 'Share Personal Wishlist'}
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', marginTop: '2px' }}>
              Share separate live checklists with different groups — they coordinate claims together
              while your surprises stay hidden from you.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="m3-btn-icon"
            style={{ width: '36px', height: '36px', flexShrink: 0 }}
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        {/* Mode Switcher: Create / Edit vs Active Links */}
        <div className="m3-connected-group">
          <button
            type="button"
            className="m3-segment-btn"
            data-kind="bought"
            data-checked={tab === 'create'}
            onClick={() => setTab('create')}
          >
            <Plus size={14} />
            {editingLinkId ? 'Editing Link' : 'New Share Link'}
          </button>
          <button
            type="button"
            className="m3-segment-btn"
            data-kind="received"
            data-checked={tab === 'manage'}
            onClick={() => {
              setEditingLinkId(null);
              setTab('manage');
            }}
          >
            <Link2 size={14} />
            Active Links ({sharedLinks.length})
          </button>
        </div>

        {tab === 'create' ? (
          <>
            {/* Group Name + Owner Display Name */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              <input
                type="text"
                value={groupTitle}
                onChange={(e) => setGroupTitle(e.target.value)}
                placeholder="Group / Link Label (e.g., Family Holiday, Friends)"
                className="m3-input"
                style={{ flex: '2 1 220px', height: '40px' }}
              />
              <input
                type="text"
                value={ownerName}
                onChange={(e) => setOwnerName(e.target.value)}
                placeholder="Your Name (e.g., Tony)"
                className="m3-input"
                style={{ flex: '1 1 140px', height: '40px' }}
              />
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
                style={{ height: '32px', fontSize: '12px', padding: '0 12px' }}
              >
                {allSelected ? <Square size={14} /> : <CheckSquare size={14} />}
                <span>
                  {allSelected ? 'Deselect All' : 'Select All'} ({selectedIds.size}/{wishes.length})
                </span>
              </button>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
                <label
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '5px',
                    fontSize: '12px',
                    cursor: 'pointer',
                    userSelect: 'none',
                  }}
                  title="Automatically include new wishes you add in the future on this link"
                >
                  <input
                    type="checkbox"
                    checked={includeAllActive}
                    onChange={(e) => {
                      const checked = e.target.checked;
                      setIncludeAllActive(checked);
                      if (checked) {
                        setSelectedIds(new Set(wishes.map((w) => w.id)));
                      }
                    }}
                    style={{ accentColor: 'var(--md-primary)', width: '15px', height: '15px' }}
                  />
                  Auto-add future wishes
                </label>

                <label
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '5px',
                    fontSize: '12px',
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
                  Notes
                </label>

                <label
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '5px',
                    fontSize: '12px',
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
                  Links
                </label>
              </div>
            </div>

            {/* Wish items checklist */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
                maxHeight: '230px',
                overflowY: 'auto',
                paddingRight: '4px',
              }}
            >
              {wishes.length === 0 ? (
                <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', padding: '14px 0' }}>
                  No active wishes right now. Enable &ldquo;Auto-add future wishes&rdquo; to create a
                  live link ready for upcoming ideas!
                </p>
              ) : (
                wishes.map((w) => {
                  const isSelected = includeAllActive || selectedIds.has(w.id);
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

            {!firebaseUser && (
              <div
                style={{
                  background: 'var(--md-surface-container)',
                  borderRadius: '14px',
                  padding: '10px 12px',
                  fontSize: '12px',
                  color: 'var(--md-on-surface-variant)',
                }}
              >
                💡 Sign in to <strong>Cloud Sync</strong> in Settings to publish live collaborative
                group links. You can still copy or share a plain-text list right now below.
              </div>
            )}

            {showPlainTextPreview && formattedShareText && (
              <div
                style={{
                  background: 'var(--md-surface-container-highest)',
                  borderRadius: '14px',
                  padding: '10px 14px',
                  fontSize: '12px',
                  maxHeight: '110px',
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
                justifyContent: 'space-between',
                gap: '8px',
                marginTop: '2px',
              }}
            >
              <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                {editingLinkId ? (
                  <button type="button" onClick={resetCreateForm} className="m3-btn-text">
                    Cancel Edit
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => setShowPlainTextPreview((v) => !v)}
                    className="m3-btn-text"
                    style={{ fontSize: '12px' }}
                  >
                    {showPlainTextPreview ? 'Hide Text Preview' : 'Plain Text Options'}
                  </button>
                )}
              </div>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                {!editingLinkId && (showPlainTextPreview || !firebaseUser) && (
                  <>
                    <button
                      type="button"
                      onClick={() => void handleCopyPlainText()}
                      disabled={!formattedShareText}
                      className="m3-btn-tonal"
                      style={{ opacity: !formattedShareText ? 0.5 : 1 }}
                    >
                      {copiedId === 'plain-text' ? <Check size={14} /> : <Copy size={14} />}
                      {copiedId === 'plain-text' ? 'Copied Text!' : 'Copy Text'}
                    </button>
                    {!firebaseUser && (
                      <button
                        type="button"
                        onClick={() => void handleSharePlainText()}
                        disabled={!formattedShareText}
                        className="m3-btn-filled"
                        style={{ opacity: !formattedShareText ? 0.5 : 1 }}
                      >
                        <Share2 size={14} /> Share Text
                      </button>
                    )}
                  </>
                )}

                {firebaseUser && (
                  <button
                    type="button"
                    onClick={() => void handleSaveLiveLink()}
                    disabled={!canCreateOrSave || savingLink}
                    className="m3-btn-filled"
                    style={{ opacity: !canCreateOrSave || savingLink ? 0.5 : 1 }}
                  >
                    <Sparkles size={15} />
                    {savingLink
                      ? 'Publishing...'
                      : editingLinkId
                      ? 'Save Link Changes'
                      : 'Create Live Group Link'}
                  </button>
                )}
              </div>
            </div>
          </>
        ) : (
          /* Manage Active Shared Links Tab */
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
              maxHeight: '390px',
              overflowY: 'auto',
              paddingRight: '4px',
            }}
          >
            {sharedLinks.length === 0 ? (
              <div
                style={{
                  padding: '28px 16px',
                  textAlign: 'center',
                  background: 'var(--md-surface-container)',
                  borderRadius: '18px',
                  color: 'var(--md-on-surface-variant)',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: '10px',
                }}
              >
                <p style={{ fontSize: '13.5px' }}>
                  You haven&rsquo;t created any live group wishlist links yet.
                </p>
                <button
                  type="button"
                  onClick={() => setTab('create')}
                  className="m3-btn-filled"
                >
                  <Plus size={15} /> Create First Group Link
                </button>
              </div>
            ) : (
              sharedLinks.map((link) => {
                const activeCount = computePublishedItemsForLink(link, wishes).length;
                const url = buildSharedWishlistUrl(link.id, firebaseConfig);
                const isShowingQr = qrLinkId === link.id;
                const isCopied = copiedId === link.id;

                return (
                  <div
                    key={link.id}
                    style={{
                      background: 'var(--md-surface-container)',
                      borderRadius: '18px',
                      padding: '14px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '10px',
                      border: isShowingQr
                        ? '1px solid var(--md-primary)'
                        : '1px solid var(--md-outline-variant)',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: '10px',
                      }}
                    >
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontWeight: 800, fontSize: '15px' }}>{link.title}</div>
                        <div
                          style={{
                            fontSize: '12px',
                            color: 'var(--md-on-surface-variant)',
                            marginTop: '2px',
                            display: 'flex',
                            flexWrap: 'wrap',
                            gap: '6px',
                            alignItems: 'center',
                          }}
                        >
                          <span>
                            {activeCount} active wish{activeCount === 1 ? '' : 'es'}
                          </span>
                          {link.includeAllActive && <span>• Auto-adds new wishes</span>}
                          {link.ownerName && <span>• Shared as {link.ownerName}</span>}
                        </div>
                      </div>

                      <div style={{ display: 'flex', gap: '4px', flexShrink: 0 }}>
                        <button
                          type="button"
                          onClick={() => handleStartEditLink(link)}
                          className="m3-btn-icon"
                          style={{ width: '32px', height: '32px' }}
                          title="Edit wishes in this group link"
                          aria-label="Edit group link"
                        >
                          <Pencil size={14} />
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            if (
                              window.confirm(
                                `Revoke "${link.title}"? People with this link will no longer see your wishlist.`
                              )
                            ) {
                              void deleteSharedWishlistLink(link.id);
                            }
                          }}
                          className="m3-btn-icon"
                          style={{ width: '32px', height: '32px' }}
                          title="Revoke / delete link"
                          aria-label="Revoke group link"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>

                    {isShowingQr && qrDataUrl && (
                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '8px 0',
                        }}
                      >
                        <div
                          style={{
                            background: '#ffffff',
                            padding: '10px',
                            borderRadius: '16px',
                            display: 'inline-flex',
                          }}
                        >
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={qrDataUrl}
                            alt={`QR Code for ${link.title}`}
                            width={168}
                            height={168}
                            style={{ display: 'block', borderRadius: '8px' }}
                          />
                        </div>
                        <span style={{ fontSize: '11.5px', color: 'var(--md-on-surface-variant)' }}>
                          Scan to open collaborative group checklist
                        </span>
                      </div>
                    )}

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                      <button
                        type="button"
                        onClick={() => void handleCopyUrl(link)}
                        className="m3-btn-filled"
                        style={{ height: '34px', fontSize: '12.5px' }}
                      >
                        {isCopied ? <Check size={14} /> : <Copy size={14} />}
                        {isCopied ? 'Copied!' : 'Copy Link'}
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleNativeShareUrl(link)}
                        className="m3-btn-tonal"
                        style={{ height: '34px', fontSize: '12.5px' }}
                      >
                        <Share2 size={14} /> Share
                      </button>

                      <button
                        type="button"
                        onClick={() => setQrLinkId(isShowingQr ? null : link.id)}
                        className="m3-btn-tonal"
                        style={{ height: '34px', fontSize: '12.5px' }}
                      >
                        <QrCode size={14} /> {isShowingQr ? 'Hide QR' : 'QR'}
                      </button>

                      <a
                        href={url}
                        onClick={(e) => {
                          e.preventDefault();
                          if (typeof window !== 'undefined') {
                            if (window.location.pathname === '/') {
                              const params = new URLSearchParams(window.location.search);
                              if (params.get('tab') !== 'wishlist') {
                                params.set('tab', 'wishlist');
                                window.history.replaceState(null, '', `/?${params.toString()}`);
                              }
                            }
                            if (url.startsWith(window.location.origin)) {
                              const relative = url.slice(window.location.origin.length) || '/';
                              router.push(relative);
                              return;
                            }
                            window.location.assign(url);
                          }
                        }}
                        className="m3-btn-tonal"
                        style={{
                          height: '34px',
                          fontSize: '12.5px',
                          textDecoration: 'none',
                        }}
                      >
                        <ExternalLink size={13} /> Preview
                      </a>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>
    </div>
  );
}
