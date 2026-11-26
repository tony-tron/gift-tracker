'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import {
  Check,
  ExternalLink,
  Eye,
  Gift,
  Loader2,
  Lock,
  MessageSquarePlus,
  Pencil,
  Plus,
  RotateCcw,
  ShoppingBag,
  Trash2,
  UserCheck,
  Users,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import {
  claimPublicWishItem,
  getOrCreateClaimerToken,
  getSavedGiverName,
  initPublicViewerServices,
  setSavedGiverName,
  setWishResearching,
  subscribeToPublicWishlist,
  subscribeToPublicWishlistClaims,
  unclaimOrStopResearchingPublicWishItem,
  updatePublicWishClaim,
} from '@/lib/firebase';
import { AddToTrackerSheet } from '@/components/modals/AddToTrackerSheet';
import type {
  PublicSharedWishlistDoc,
  PublishedWishItem,
  SavedWishlist,
  SharedWishClaim,
} from '@/lib/types';

interface SavedWishlistChecklistPanelProps {
  savedWishlist: SavedWishlist;
  onEdit: (sw: SavedWishlist) => void;
  onDelete: (id: string) => void;
  onLogExternalGift?: (personId: string | null, url?: string) => void;
  compactInline?: boolean;
}

export function SavedWishlistChecklistPanel({
  savedWishlist,
  onEdit,
  onDelete,
  onLogExternalGift,
  compactInline = false,
}: SavedWishlistChecklistPanelProps) {
  const {
    people,
    gifts,
    firebaseUser,
    setActiveTab,
    setSelectedPersonId,
    showSnackbar,
  } = useGiftStore();

  const linkedPerson = useMemo(
    () => people.find((p) => p.id === savedWishlist.personId) || null,
    [people, savedWishlist.personId]
  );

  const shareId = savedWishlist.type === 'live' ? savedWishlist.shareId || '' : '';
  const services = useMemo(
    () => (shareId ? initPublicViewerServices(savedWishlist.projectKey) : null),
    [shareId, savedWishlist.projectKey]
  );

  const [loading, setLoading] = useState<boolean>(() => Boolean(services && shareId));
  const [liveDoc, setLiveDoc] = useState<PublicSharedWishlistDoc | null>(null);
  const [claims, setClaims] = useState<Record<string, SharedWishClaim>>({});
  const [viewerUid, setViewerUid] = useState<string | null>(null);
  const [authReady, setAuthReady] = useState<boolean>(() => !services);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [giverName, setGiverName] = useState<string>(() => {
    const saved = getSavedGiverName();
    if (saved) return saved;
    return firebaseUser?.displayName?.split(' ')[0] || '';
  });
  const [claimerToken] = useState<string>(getOrCreateClaimerToken);
  const [editingName, setEditingName] = useState(false);
  const [nameDraft, setNameDraft] = useState('');

  // Pending claim name prompt when giverName is blank
  const [pendingAction, setPendingAction] = useState<{
    wishId: string;
    kind: 'claim' | 'research';
  } | null>(null);
  const [pendingNameInput, setPendingNameInput] = useState('');
  const [busyWishId, setBusyWishId] = useState<string | null>(null);

  // Secret giver note inline editor
  const [editingNoteWishId, setEditingNoteWishId] = useState<string | null>(null);
  const [giverNoteDraft, setGiverNoteDraft] = useState('');

  // Filter & AddToTracker modal
  const [filter, setFilter] = useState<'all' | 'available' | 'mine'>('all');
  const [trackerSheetWish, setTrackerSheetWish] = useState<PublishedWishItem | null>(null);

  useEffect(() => {
    if (!services) return;
    const unsub = onAuthStateChanged(services.auth, (user) => {
      setViewerUid(user?.uid ?? null);
      setAuthReady(true);
    });
    return () => unsub();
  }, [services]);

  useEffect(() => {
    if (!services || !shareId) return;
    const unsub = subscribeToPublicWishlist(
      services.db,
      shareId,
      (docData) => {
        setLiveDoc(docData);
        setErrorMsg(null);
        setLoading(false);
      },
      (err) => {
        setErrorMsg(err.message);
        setLoading(false);
      }
    );
    return () => unsub();
  }, [services, shareId]);

  const isOwnerPreview = Boolean(
    authReady && liveDoc && viewerUid && viewerUid === liveDoc.ownerUid
  );

  useEffect(() => {
    if (!services || !shareId || !authReady || !liveDoc || isOwnerPreview) return;
    const unsub = subscribeToPublicWishlistClaims(
      services.db,
      shareId,
      (claimsMap) => setClaims(claimsMap),
      () => {}
    );
    return () => unsub();
  }, [services, shareId, authReady, liveDoc, isOwnerPreview]);

  const sortedItems = useMemo(() => {
    if (!liveDoc?.items) return [];
    return [...liveDoc.items].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  }, [liveDoc]);

  const isClaimMine = (claim: SharedWishClaim | undefined): boolean => {
    if (!claim) return false;
    if (claim.claimerToken && claim.claimerToken === claimerToken) return true;
    if (claim.researcherTokens && claim.researcherTokens.includes(claimerToken)) return true;
    const cleanGiver = giverName.trim().toLowerCase();
    if (cleanGiver && claim.claimedBy.trim().toLowerCase() === cleanGiver) return true;
    if (
      cleanGiver &&
      claim.researchers &&
      claim.researchers.some((r) => r.trim().toLowerCase() === cleanGiver)
    ) {
      return true;
    }
    return false;
  };

  const counts = useMemo(() => {
    let available = 0;
    let mine = 0;
    sortedItems.forEach((item) => {
      const c = claims[item.id];
      if (!c || c.status === 'researching') available += 1;
      if (isClaimMine(c)) mine += 1;
    });
    return {
      all: sortedItems.length,
      available,
      claimed: sortedItems.length - available,
      mine,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortedItems, claims, claimerToken, giverName]);

  const displayedItems = useMemo(() => {
    if (isOwnerPreview || filter === 'all') return sortedItems;
    if (filter === 'available') {
      return sortedItems.filter((i) => !claims[i.id] || claims[i.id].status === 'researching');
    }
    return sortedItems.filter((i) => isClaimMine(claims[i.id]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortedItems, claims, filter, isOwnerPreview, claimerToken, giverName]);

  const ownerDisplay =
    liveDoc?.ownerName?.trim() ||
    savedWishlist.ownerName?.trim() ||
    linkedPerson?.name ||
    'the wishlist owner';

  const commitGiverName = (nextName: string) => {
    const clean = nextName.trim();
    setGiverName(clean);
    setSavedGiverName(clean);
  };

  const executeClaimOrResearch = async (
    wishId: string,
    kind: 'claim' | 'research',
    chosenName: string
  ) => {
    if (!services || !shareId || !chosenName.trim()) return;
    setBusyWishId(wishId);
    try {
      if (kind === 'claim') {
        const res = await claimPublicWishItem(services.db, shareId, {
          wishId,
          claimedBy: chosenName.trim(),
          claimerToken,
          purchased: false,
          status: 'reserved',
          researchers: [chosenName.trim()],
          researcherTokens: [claimerToken],
          giverNote: '',
          claimedAt: new Date().toISOString(),
        });
        if (!res.ok) {
          showSnackbar(`Heads up — ${res.claimedBy} just claimed this item!`);
        } else {
          showSnackbar(`Claimed! Hidden from ${ownerDisplay}.`);
        }
      } else {
        const res = await setWishResearching(
          services.db,
          shareId,
          wishId,
          chosenName.trim(),
          claimerToken
        );
        if (!res.ok) {
          showSnackbar(`Already claimed by ${res.claimedBy}.`);
        } else {
          showSnackbar(`Marked as Researching (hidden from ${ownerDisplay}).`);
        }
      }
    } catch {
      showSnackbar('Could not update claim status right now.');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleActionClick = (wishId: string, kind: 'claim' | 'research') => {
    if (!giverName.trim()) {
      setPendingAction({ wishId, kind });
      setPendingNameInput('');
      return;
    }
    void executeClaimOrResearch(wishId, kind, giverName);
  };

  const handleTogglePurchased = async (claim: SharedWishClaim) => {
    if (!services || !shareId) return;
    setBusyWishId(claim.wishId);
    try {
      const nextPurchased = !claim.purchased;
      await updatePublicWishClaim(services.db, shareId, claim.wishId, claim, {
        purchased: nextPurchased,
        status: nextPurchased ? 'purchased' : 'reserved',
      });
      showSnackbar(nextPurchased ? 'Marked as Purchased!' : 'Marked as Reserved (not yet bought).');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleReleaseClaim = async (wishId: string) => {
    if (!services || !shareId) return;
    setBusyWishId(wishId);
    try {
      await unclaimOrStopResearchingPublicWishItem(services.db, shareId, wishId, claimerToken);
      showSnackbar('Released claim on wishlist item.');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleSaveNote = async (claim: SharedWishClaim) => {
    if (!services || !shareId) return;
    setBusyWishId(claim.wishId);
    try {
      await updatePublicWishClaim(services.db, shareId, claim.wishId, claim, {
        giverNote: giverNoteDraft,
      });
      setEditingNoteWishId(null);
      showSnackbar('Saved secret note for other gift-givers!');
    } finally {
      setBusyWishId(null);
    }
  };

  return (
    <div
      className={compactInline ? undefined : 'm3-surface-card'}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '14px',
        padding: compactInline ? '14px 0 2px' : '20px',
        borderTop: compactInline ? '1px solid var(--md-outline-variant)' : undefined,
        marginTop: compactInline ? '12px' : undefined,
      }}
    >
      {/* Header row (shown in 2-pane detail mode) */}
      {!compactInline && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: 0 }}>
            <div
              className="m3-avatar"
              data-shape={linkedPerson?.avatarShape || 'clover'}
              data-tone={linkedPerson?.avatarTone || 'teal'}
              style={{ width: '48px', height: '48px', fontSize: '16px', flexShrink: 0 }}
            >
              {(linkedPerson?.name || ownerDisplay).slice(0, 2).toUpperCase()}
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '8px' }}>
                <h3 style={{ fontSize: '19px', fontWeight: 800, lineHeight: 1.2 }}>
                  {liveDoc?.title || savedWishlist.title}
                </h3>
                <span
                  style={{
                    fontSize: '11px',
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: '999px',
                    background:
                      savedWishlist.type === 'live'
                        ? 'var(--md-success-container)'
                        : 'var(--md-secondary-container)',
                    color:
                      savedWishlist.type === 'live'
                        ? 'var(--md-on-success-container)'
                        : 'var(--md-on-secondary-container)',
                  }}
                >
                  {savedWishlist.type === 'live' ? '● Live Synced' : 'External Registry'}
                </span>
              </div>

              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  alignItems: 'center',
                  gap: '8px',
                  marginTop: '4px',
                  fontSize: '12.5px',
                  color: 'var(--md-on-surface-variant)',
                }}
              >
                <span>From {ownerDisplay}</span>
                {linkedPerson && (
                  <>
                    <span>•</span>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedPersonId(linkedPerson.id);
                        setActiveTab('people');
                      }}
                      style={{
                        color: 'var(--md-primary)',
                        fontWeight: 700,
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      <Users size={12} /> {linkedPerson.name} Profile →
                    </button>
                  </>
                )}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
            <a
              href={
                savedWishlist.type === 'live' && shareId
                  ? `/w/${encodeURIComponent(shareId)}${
                      savedWishlist.projectKey
                        ? `?k=${encodeURIComponent(savedWishlist.projectKey)}`
                        : ''
                    }`
                  : savedWishlist.url
              }
              target="_blank"
              rel="noopener noreferrer"
              className="m3-btn-tonal"
              style={{ height: '34px', fontSize: '12px', textDecoration: 'none' }}
            >
              <ExternalLink size={13} />
              {savedWishlist.type === 'live' ? 'Full Page' : 'Open Link'}
            </a>
            <button
              type="button"
              onClick={() => onEdit(savedWishlist)}
              className="m3-btn-icon"
              style={{ width: '34px', height: '34px' }}
              title="Edit saved wishlist"
              aria-label="Edit saved wishlist"
            >
              <Pencil size={14} />
            </button>
            <button
              type="button"
              onClick={() => onDelete(savedWishlist.id)}
              className="m3-btn-icon"
              style={{ width: '34px', height: '34px' }}
              title="Remove saved wishlist"
              aria-label="Remove saved wishlist"
            >
              <Trash2 size={14} />
            </button>
          </div>
        </div>
      )}

      {/* Private Notes Banner */}
      {savedWishlist.notes && (
        <div
          style={{
            background: 'var(--md-surface-container-low)',
            padding: '8px 12px',
            borderRadius: '12px',
            borderLeft: '3px solid var(--md-tertiary)',
            fontSize: '12.5px',
            color: 'var(--md-on-surface)',
          }}
        >
          <strong>Your private note:</strong> {savedWishlist.notes}
        </div>
      )}

      {/* EXTERNAL REGISTRY VIEW */}
      {savedWishlist.type === 'external' ? (
        <div
          style={{
            background: 'var(--md-surface-container-low)',
            borderRadius: '18px',
            padding: '18px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', lineHeight: 1.5 }}>
            This is an external wishlist or registry link hosted outside Gift Tracker. Open it in a
            new tab to browse items, then log any gift you pick into your Occasions tracker so your
            budgets stay accurate.
          </p>

          <div
            style={{
              fontSize: '12px',
              color: 'var(--md-on-surface-variant)',
              wordBreak: 'break-all',
              background: 'var(--md-surface-container)',
              padding: '8px 12px',
              borderRadius: '10px',
            }}
          >
            {savedWishlist.url}
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            <a
              href={savedWishlist.url}
              target="_blank"
              rel="noopener noreferrer"
              className="m3-btn-filled"
              style={{ textDecoration: 'none' }}
            >
              <ExternalLink size={15} /> Open External Registry
            </a>
            {onLogExternalGift && (
              <button
                type="button"
                onClick={() => onLogExternalGift(savedWishlist.personId, savedWishlist.url)}
                className="m3-btn-tonal"
              >
                <Plus size={15} /> Log a Gift from This List
              </button>
            )}
          </div>
        </div>
      ) : !services ? (
        /* LIVE WISHLIST WITHOUT FIREBASE CONFIG */
        <div
          style={{
            background: 'var(--md-surface-container-low)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
          }}
        >
          <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)' }}>
            Connect Firebase Cloud Sync in Settings (or ask for the full link with <code>?k=...</code>)
            to load live claims right here, or open the link directly in a new tab.
          </p>
          <div>
            <a
              href={savedWishlist.url}
              target="_blank"
              rel="noopener noreferrer"
              className="m3-btn-filled"
              style={{ textDecoration: 'none' }}
            >
              <ExternalLink size={15} /> Open Wishlist Page
            </a>
          </div>
        </div>
      ) : loading ? (
        <div
          style={{
            padding: '28px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '10px',
            color: 'var(--md-on-surface-variant)',
            fontSize: '13.5px',
          }}
        >
          <Loader2 size={18} /> Loading live checklist...
        </div>
      ) : errorMsg || !liveDoc ? (
        <div
          style={{
            background: 'var(--md-surface-container-low)',
            borderRadius: '16px',
            padding: '18px',
            fontSize: '13px',
            color: 'var(--md-on-surface-variant)',
          }}
        >
          {errorMsg ||
            'This shared wishlist was not found or may have been revoked by its owner.'}
        </div>
      ) : (
        /* LIVE COLLABORATIVE CHECKLIST */
        <>
          {/* Privacy + Giver Identity Row */}
          <div
            style={{
              background: 'var(--md-surface-container-low)',
              borderRadius: '14px',
              padding: '10px 12px',
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '8px',
              fontSize: '12px',
            }}
          >
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                color: 'var(--md-on-surface-variant)',
              }}
            >
              <Lock size={13} style={{ color: 'var(--md-primary)', flexShrink: 0 }} />
              {isOwnerPreview ? (
                <span>
                  <strong>Owner Preview:</strong> Claims stay hidden from you so surprises aren&rsquo;t spoiled.
                </span>
              ) : (
                <span>
                  Claims &amp; group notes are <strong>strictly hidden from {ownerDisplay}</strong>.
                </span>
              )}
            </span>

            {!isOwnerPreview && (
              <div>
                {editingName ? (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      commitGiverName(nameDraft);
                      setEditingName(false);
                    }}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                  >
                    <input
                      type="text"
                      value={nameDraft}
                      onChange={(e) => setNameDraft(e.target.value)}
                      placeholder="Your name..."
                      autoFocus
                      className="m3-input"
                      style={{ height: '30px', width: '130px', fontSize: '12px' }}
                    />
                    <button
                      type="submit"
                      className="m3-btn-filled"
                      style={{ height: '30px', fontSize: '11.5px', padding: '0 10px' }}
                    >
                      Save
                    </button>
                  </form>
                ) : (
                  <button
                    type="button"
                    onClick={() => {
                      setNameDraft(giverName);
                      setEditingName(true);
                    }}
                    className="m3-btn-tonal"
                    style={{ height: '30px', fontSize: '11.5px', padding: '0 10px' }}
                  >
                    <UserCheck size={12} />
                    {giverName ? `Claiming as ${giverName}` : 'Set Your Name'}
                    <Pencil size={11} style={{ opacity: 0.7 }} />
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Prompt when giverName is missing on first Claim / Research tap */}
          {pendingAction && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!pendingNameInput.trim()) return;
                const chosen = pendingNameInput.trim();
                commitGiverName(chosen);
                const act = pendingAction;
                setPendingAction(null);
                void executeClaimOrResearch(act.wishId, act.kind, chosen);
              }}
              style={{
                background: 'var(--md-secondary-container)',
                color: 'var(--md-on-secondary-container)',
                padding: '12px 14px',
                borderRadius: '14px',
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                gap: '8px',
              }}
            >
              <span style={{ fontSize: '12.5px', fontWeight: 700, flex: '1 1 200px' }}>
                Enter your name for other gift-givers (hidden from {ownerDisplay}):
              </span>
              <input
                type="text"
                required
                autoFocus
                value={pendingNameInput}
                onChange={(e) => setPendingNameInput(e.target.value)}
                placeholder="e.g., Sarah, Brother..."
                className="m3-input"
                style={{ height: '34px', width: '160px', fontSize: '12.5px' }}
              />
              <button type="submit" className="m3-btn-filled" style={{ height: '34px', fontSize: '12px' }}>
                Continue
              </button>
              <button
                type="button"
                onClick={() => setPendingAction(null)}
                className="m3-btn-text"
                style={{ height: '34px', fontSize: '12px' }}
              >
                Cancel
              </button>
            </form>
          )}

          {/* Filter Chips */}
          {!isOwnerPreview && sortedItems.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
              <button
                type="button"
                className="m3-chip"
                data-selected={filter === 'all'}
                onClick={() => setFilter('all')}
                style={{ height: '32px', fontSize: '12px', padding: '0 12px' }}
              >
                All ({counts.all})
              </button>
              <button
                type="button"
                className="m3-chip"
                data-selected={filter === 'available'}
                onClick={() => setFilter('available')}
                style={{ height: '32px', fontSize: '12px', padding: '0 12px' }}
              >
                Available ({counts.available})
              </button>
              <button
                type="button"
                className="m3-chip"
                data-selected={filter === 'mine'}
                onClick={() => setFilter('mine')}
                style={{ height: '32px', fontSize: '12px', padding: '0 12px' }}
              >
                Claimed by Me ({counts.mine})
              </button>
            </div>
          )}

          {/* Items List */}
          {displayedItems.length === 0 ? (
            <div
              style={{
                padding: '22px 16px',
                textAlign: 'center',
                background: 'var(--md-surface-container-low)',
                borderRadius: '14px',
                fontSize: '13px',
                color: 'var(--md-on-surface-variant)',
              }}
            >
              {sortedItems.length === 0
                ? `${ownerDisplay} hasn't added any active wishes to this list yet.`
                : 'No items match this filter.'}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {displayedItems.map((item) => {
                const claim = claims[item.id];
                const mine = isClaimMine(claim);
                const isResearching = claim?.status === 'researching';
                const isClaimedByOther = Boolean(claim && !isResearching && !mine);
                const isBusy = busyWishId === item.id;
                const trackedGift = gifts.find(
                  (g) => g.sourceWishlistShareId === shareId && g.sourceWishId === item.id
                );

                return (
                  <div
                    key={item.id}
                    style={{
                      background: 'var(--md-surface-container-low)',
                      borderRadius: '16px',
                      padding: '12px 14px',
                      opacity: isClaimedByOther ? 0.65 : 1,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: '10px',
                      }}
                    >
                      <div style={{ minWidth: 0, flex: '1 1 200px' }}>
                        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px' }}>
                          <span
                            style={{
                              fontSize: '14.5px',
                              fontWeight: 700,
                              textDecoration: isClaimedByOther ? 'line-through' : 'none',
                            }}
                          >
                            {item.title}
                          </span>
                          {item.priority === 'high' && (
                            <span
                              style={{
                                fontSize: '10.5px',
                                fontWeight: 700,
                                padding: '2px 7px',
                                borderRadius: '999px',
                                background: 'var(--md-primary-container)',
                                color: 'var(--md-on-primary-container)',
                              }}
                            >
                              Top Pick
                            </span>
                          )}
                        </div>

                        {item.notes && (
                          <p
                            style={{
                              fontSize: '12.5px',
                              color: 'var(--md-on-surface-variant)',
                              marginTop: '3px',
                              whiteSpace: 'pre-wrap',
                            }}
                          >
                            {item.notes}
                          </p>
                        )}

                        <div
                          style={{
                            display: 'flex',
                            flexWrap: 'wrap',
                            alignItems: 'center',
                            gap: '10px',
                            marginTop: '4px',
                            fontSize: '12px',
                          }}
                        >
                          {item.url && (
                            <a
                              href={item.url}
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
                              <ExternalLink size={12} /> Product Link
                            </a>
                          )}

                          {!isOwnerPreview && isResearching && claim && (
                            <span style={{ color: 'var(--md-warning)', fontWeight: 600 }}>
                              👀 Researching: {claim.claimedBy}
                            </span>
                          )}

                          {!isOwnerPreview && claim?.giverNote && (
                            <span style={{ color: 'var(--md-on-surface-variant)' }}>
                              💬 Group note: &ldquo;{claim.giverNote}&rdquo;
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Item Actions */}
                      {!isOwnerPreview && (
                        <div
                          style={{
                            display: 'flex',
                            flexWrap: 'wrap',
                            alignItems: 'center',
                            gap: '6px',
                          }}
                        >
                          {isClaimedByOther ? (
                            <span
                              style={{
                                fontSize: '12px',
                                fontWeight: 600,
                                padding: '6px 10px',
                                borderRadius: '10px',
                                background: 'var(--md-surface-container-high)',
                                color: 'var(--md-on-surface-variant)',
                              }}
                            >
                              {claim?.purchased ? '🛍️ Bought by ' : 'Claimed by '}
                              {claim?.claimedBy}
                            </span>
                          ) : mine && claim && !isResearching ? (
                            <>
                              <button
                                type="button"
                                disabled={isBusy}
                                onClick={() => void handleTogglePurchased(claim)}
                                className="m3-btn-tonal"
                                style={{
                                  height: '32px',
                                  fontSize: '11.5px',
                                  padding: '0 10px',
                                  background: claim.purchased
                                    ? 'var(--md-success-container)'
                                    : 'var(--md-secondary-container)',
                                  color: claim.purchased
                                    ? 'var(--md-on-success-container)'
                                    : 'var(--md-on-secondary-container)',
                                }}
                                title="Toggle Purchased vs Reserved"
                              >
                                {claim.purchased ? (
                                  <>
                                    <ShoppingBag size={12} /> Purchased by You
                                  </>
                                ) : (
                                  <>
                                    <Check size={13} /> Claimed by You
                                  </>
                                )}
                              </button>

                              <button
                                type="button"
                                onClick={() => {
                                  setEditingNoteWishId(
                                    editingNoteWishId === item.id ? null : item.id
                                  );
                                  setGiverNoteDraft(claim.giverNote || '');
                                }}
                                className="m3-btn-icon"
                                style={{ width: '30px', height: '30px' }}
                                title="Leave secret note for other gift-givers"
                                aria-label="Secret giver note"
                              >
                                <MessageSquarePlus size={14} />
                              </button>

                              <button
                                type="button"
                                disabled={isBusy}
                                onClick={() => void handleReleaseClaim(item.id)}
                                className="m3-btn-icon"
                                style={{ width: '30px', height: '30px' }}
                                title="Release claim"
                                aria-label="Release claim"
                              >
                                <RotateCcw size={13} />
                              </button>
                            </>
                          ) : (
                            <>
                              {mine && isResearching ? (
                                <button
                                  type="button"
                                  disabled={isBusy}
                                  onClick={() => void handleReleaseClaim(item.id)}
                                  className="m3-btn-tonal"
                                  style={{ height: '32px', fontSize: '11.5px', padding: '0 10px' }}
                                >
                                  <Eye size={13} /> Researching (Stop)
                                </button>
                              ) : (
                                <button
                                  type="button"
                                  disabled={isBusy}
                                  onClick={() => handleActionClick(item.id, 'research')}
                                  className="m3-btn-tonal"
                                  style={{ height: '32px', fontSize: '11.5px', padding: '0 10px' }}
                                >
                                  <Eye size={13} /> Research
                                </button>
                              )}

                              <button
                                type="button"
                                disabled={isBusy}
                                onClick={() => handleActionClick(item.id, 'claim')}
                                className="m3-btn-filled"
                                style={{ height: '32px', fontSize: '11.5px', padding: '0 12px' }}
                              >
                                <Check size={13} /> Claim
                              </button>
                            </>
                          )}

                          {!isClaimedByOther && (
                            trackedGift ? (
                              <button
                                type="button"
                                onClick={() => setActiveTab(trackedGift.eventId ? 'events' : 'ideas')}
                                className="m3-btn-tonal"
                                style={{ height: '32px', fontSize: '11.5px', padding: '0 10px' }}
                                title="View in your Gift Tracker"
                              >
                                <Gift size={12} /> In Tracker →
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => setTrackerSheetWish(item)}
                                className="m3-btn-tonal"
                                style={{ height: '32px', fontSize: '11.5px', padding: '0 10px' }}
                              >
                                <Plus size={12} /> Track Gift
                              </button>
                            )
                          )}
                        </div>
                      )}
                    </div>

                    {/* Secret Giver Note Inline Form */}
                    {editingNoteWishId === item.id && claim && (
                      <form
                        onSubmit={(e) => {
                          e.preventDefault();
                          void handleSaveNote(claim);
                        }}
                        style={{
                          display: 'flex',
                          flexWrap: 'wrap',
                          gap: '6px',
                          paddingTop: '6px',
                          borderTop: '1px solid var(--md-outline-variant)',
                        }}
                      >
                        <input
                          type="text"
                          value={giverNoteDraft}
                          onChange={(e) => setGiverNoteDraft(e.target.value)}
                          placeholder={`Secret note for other givers (hidden from ${ownerDisplay})...`}
                          className="m3-input"
                          style={{ flex: '1 1 200px', height: '34px', fontSize: '12.5px' }}
                        />
                        <button
                          type="submit"
                          className="m3-btn-filled"
                          style={{ height: '34px', fontSize: '12px' }}
                        >
                          Save Note
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingNoteWishId(null)}
                          className="m3-btn-text"
                          style={{ height: '34px', fontSize: '12px' }}
                        >
                          Cancel
                        </button>
                      </form>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      {trackerSheetWish && shareId && (
        <AddToTrackerSheet
          open={Boolean(trackerSheetWish)}
          onClose={() => setTrackerSheetWish(null)}
          wish={trackerSheetWish}
          wishlistOwnerName={ownerDisplay}
          preferredPersonId={savedWishlist.personId}
          shareId={shareId}
          fallbackKey={savedWishlist.projectKey}
          giverName={giverName}
          onGiverNameChange={commitGiverName}
          claimerToken={claimerToken}
          onAddedToTracker={() => {
            setTrackerSheetWish(null);
          }}
        />
      )}
    </div>
  );
}
