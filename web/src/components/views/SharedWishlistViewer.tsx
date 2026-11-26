'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { onAuthStateChanged } from 'firebase/auth';
import {
  ArrowLeft,
  Bookmark,
  Check,
  ExternalLink,
  EyeOff,
  Gift,
  Loader2,
  Lock,
  MessageSquarePlus,
  Moon,
  Pencil,
  RotateCcw,
  Share2,
  ShoppingBag,
  Sparkles,
  Sun,
  UserCheck,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import {
  claimPublicWishItem,
  getOrCreateClaimerToken,
  getSavedGiverName,
  initPublicViewerServices,
  setSavedGiverName,
  subscribeToPublicWishlist,
  subscribeToPublicWishlistClaims,
  unclaimOrStopResearchingPublicWishItem,
  updatePublicWishClaim,
} from '@/lib/firebase';
import type { PublicSharedWishlistDoc, PublishedWishItem, SharedWishClaim } from '@/lib/types';
import { AddToTrackerSheet } from '@/components/modals/AddToTrackerSheet';

export function SharedWishlistViewer() {
  const params = useParams<{ shareId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { colorMode, setColorMode, gifts, savedWishlists, addSavedWishlist } = useGiftStore();

  const shareId = typeof params?.shareId === 'string' ? params.shareId : '';
  const fallbackKey = searchParams.get('k') || searchParams.get('pair');

  const services = useMemo(
    () => initPublicViewerServices(fallbackKey),
    [fallbackKey]
  );

  const [loading, setLoading] = useState<boolean>(() => Boolean(services && shareId));
  const [wishlist, setWishlist] = useState<PublicSharedWishlistDoc | null>(null);
  const [claims, setClaims] = useState<Record<string, SharedWishClaim>>({});
  const [viewerUid, setViewerUid] = useState<string | null>(null);
  const [authReady, setAuthReady] = useState<boolean>(() => !services);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Giver identity persisted locally in browser
  const [giverName, setGiverName] = useState<string>(() => {
    return getSavedGiverName();
  });
  const [claimerToken] = useState<string>(getOrCreateClaimerToken);
  const [editingName, setEditingName] = useState(false);
  const [nameDraft, setNameDraft] = useState('');
  const [trackerSheetWish, setTrackerSheetWish] = useState<PublishedWishItem | null>(null);

  // Pending claim modal when giver hasn't set their name yet
  const [pendingClaimWishId, setPendingClaimWishId] = useState<string | null>(null);
  const [pendingNameInput, setPendingNameInput] = useState('');
  const [busyWishId, setBusyWishId] = useState<string | null>(null);

  // Secret giver note inline editor
  const [editingNoteWishId, setEditingNoteWishId] = useState<string | null>(null);
  const [giverNoteDraft, setGiverNoteDraft] = useState('');

  // Filter & toast
  const [filter, setFilter] = useState<'all' | 'available' | 'mine'>('all');
  const [toast, setToast] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => {
      setToast((prev) => (prev === msg ? null : prev));
    }, 3600);
  };

  // Observe current auth user (so if the owner opens their own link, we enter Owner Preview Mode!)
  useEffect(() => {
    if (!services) return;
    const unsub = onAuthStateChanged(services.auth, (user) => {
      setViewerUid(user?.uid ?? null);
      setAuthReady(true);
    });
    return () => unsub();
  }, [services]);

  // Subscribe to the public shared_wishlists/{shareId} document
  useEffect(() => {
    if (!services || !shareId) return;
    const unsub = subscribeToPublicWishlist(
      services.db,
      shareId,
      (docData) => {
        setWishlist(docData);
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
    authReady && wishlist && viewerUid && viewerUid === wishlist.ownerUid
  );

  // Subscribe to secret claims ONLY when viewer is NOT the wishlist owner
  useEffect(() => {
    if (!services || !shareId || !authReady || !wishlist || isOwnerPreview) return;
    const unsub = subscribeToPublicWishlistClaims(
      services.db,
      shareId,
      (claimsMap) => {
        setClaims(claimsMap);
      },
      () => {
        // Ignore claim read errors if owner auth state transitions
      }
    );
    return () => unsub();
  }, [services, shareId, authReady, wishlist, isOwnerPreview]);

  const sortedItems = useMemo(() => {
    if (!wishlist?.items) return [];
    return [...wishlist.items].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  }, [wishlist]);

  const isClaimMine = (claim: SharedWishClaim | undefined): boolean => {
    if (!claim) return false;
    if (claim.claimerToken && claim.claimerToken === claimerToken) return true;
    if (claim.researcherTokens && claim.researcherTokens.includes(claimerToken)) return true;
    if (giverName.trim() && claim.claimedBy.trim().toLowerCase() === giverName.trim().toLowerCase()) {
      return true;
    }
    if (
      giverName.trim() &&
      claim.researchers &&
      claim.researchers.some((r) => r.trim().toLowerCase() === giverName.trim().toLowerCase())
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

  const saveGiverName = (nextName: string) => {
    const clean = nextName.trim();
    setGiverName(clean);
    setSavedGiverName(clean);
  };

  const executeClaim = async (wishId: string, chosenName: string) => {
    if (!services || !shareId || !chosenName.trim()) return;
    setBusyWishId(wishId);
    try {
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
        showToast(`Heads up — ${res.claimedBy} just claimed this item!`);
      } else {
        showToast('Claimed! Other gift-givers will now see this is reserved.');
      }
    } catch {
      showToast('Could not save claim right now. Please try again.');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleTapClaim = (wishId: string) => {
    if (!giverName.trim()) {
      setPendingClaimWishId(wishId);
      setPendingNameInput('');
      return;
    }
    void executeClaim(wishId, giverName);
  };

  const handleTogglePurchased = async (claim: SharedWishClaim) => {
    if (!services || !shareId) return;
    setBusyWishId(claim.wishId);
    try {
      const nextPurchased = !claim.purchased;
      await updatePublicWishClaim(services.db, shareId, claim.wishId, claim, {
        purchased: nextPurchased,
        status: nextPurchased ? 'purchased' : 'researching',
      });
      showToast(nextPurchased ? 'Marked as Purchased!' : 'Reverted to Researching (open to others)');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleSaveGiverNote = async (claim: SharedWishClaim) => {
    if (!services || !shareId) return;
    setBusyWishId(claim.wishId);
    try {
      await updatePublicWishClaim(services.db, shareId, claim.wishId, claim, {
        giverNote: giverNoteDraft,
      });
      setEditingNoteWishId(null);
      showToast('Saved note for other gift-givers!');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleUnclaim = async (claim: SharedWishClaim, mine: boolean) => {
    if (!services || !shareId) return;
    if (
      !mine &&
      !window.confirm(
        `This item was claimed by ${claim.claimedBy}. Release their claim so someone else can get it?`
      )
    ) {
      return;
    }
    setBusyWishId(claim.wishId);
    try {
      await unclaimOrStopResearchingPublicWishItem(services.db, shareId, claim.wishId, claimerToken);
      showToast('Released claim / research — item is available.');
    } finally {
      setBusyWishId(null);
    }
  };

  const handleSharePage = async () => {
    if (typeof window === 'undefined') return;
    const url = window.location.href;
    if (navigator.share) {
      try {
        await navigator.share({
          title: wishlist?.title || 'Shared Gift Wishlist',
          url,
        });
        return;
      } catch {
        // Fallback to copy
      }
    }
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      showToast('Copied wishlist link to clipboard!');
    }
  };

  const toggleLightDark = () => {
    setColorMode(colorMode === 'light' ? 'dark' : 'light');
  };

  if (!services) {
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '24px',
        }}
      >
        <div
          className="m3-surface-card"
          style={{ maxWidth: '460px', textAlign: 'center', padding: '28px 24px' }}
        >
          <Gift size={32} style={{ color: 'var(--md-primary)', margin: '0 auto 10px' }} />
          <h1 style={{ fontSize: '20px', fontWeight: 800 }}>Wishlist Connection Missing</h1>
          <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', marginTop: '8px' }}>
            This shared wishlist link is missing its Firebase connection key. Ask the sender to
            re-copy the link from their Gift Tracker app.
          </p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '12px',
        }}
      >
        <Loader2 size={28} style={{ color: 'var(--md-primary)' }} />
        <p style={{ fontWeight: 600, fontSize: '15px' }}>Loading live wishlist...</p>
      </div>
    );
  }

  if (!wishlist) {
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '24px',
        }}
      >
        <div
          className="m3-surface-card"
          style={{ maxWidth: '460px', textAlign: 'center', padding: '30px 24px' }}
        >
          <Gift size={32} style={{ color: 'var(--md-primary)', margin: '0 auto 10px' }} />
          <h1 style={{ fontSize: '20px', fontWeight: 800 }}>Wishlist Not Found or Revoked</h1>
          <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', marginTop: '8px' }}>
            {errorMsg ||
              'This wishlist link may have been revoked or replaced with a newer link by its owner.'}
          </p>
        </div>
      </div>
    );
  }

  const ownerDisplay = wishlist.ownerName?.trim() || 'the wishlist owner';

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: 'var(--md-bg)',
        color: 'var(--md-on-surface)',
        padding: '18px 16px 64px',
      }}
    >
      <div
        style={{
          maxWidth: '780px',
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px',
        }}
      >
        {/* Top Bar */}
        <header
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '10px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            {isOwnerPreview && (
              <button
                type="button"
                onClick={() => {
                  if (typeof window !== 'undefined' && window.history.length > 1) {
                    router.back();
                  } else {
                    router.push('/?tab=wishlist');
                  }
                }}
                className="m3-btn-icon"
                title="Back to Wishlist"
                aria-label="Back to Wishlist"
              >
                <ArrowLeft size={18} />
              </button>
            )}
            <div
              className="m3-avatar"
              data-shape="clover"
              data-tone="terracotta"
              style={{ width: '38px', height: '38px' }}
            >
              <Sparkles size={17} />
            </div>
            <div>
              <div style={{ fontWeight: 800, fontSize: '15px', letterSpacing: '-0.01em' }}>
                Gift Tracker
              </div>
              <div style={{ fontSize: '11.5px', color: 'var(--md-on-surface-variant)' }}>
                Collaborative Group Checklist
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {!isOwnerPreview && (
              <>
                {editingName ? (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      saveGiverName(nameDraft);
                      setEditingName(false);
                    }}
                    style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                  >
                    <input
                      type="text"
                      value={nameDraft}
                      onChange={(e) => setNameDraft(e.target.value)}
                      placeholder="Your name..."
                      autoFocus
                      className="m3-input"
                      style={{ height: '34px', width: '140px', fontSize: '12.5px' }}
                    />
                    <button type="submit" className="m3-btn-filled" style={{ height: '34px' }}>
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
                    style={{ height: '36px', fontSize: '12.5px' }}
                  >
                    <UserCheck size={14} />
                    {giverName ? `${giverName}` : 'Set Your Name'}
                    <Pencil size={12} style={{ opacity: 0.7 }} />
                  </button>
                )}
              </>
            )}

            <button
              type="button"
              onClick={() => void handleSharePage()}
              className="m3-btn-icon"
              title="Share link with group"
              aria-label="Share wishlist link"
            >
              <Share2 size={17} />
            </button>

            <button
              type="button"
              onClick={toggleLightDark}
              className="m3-btn-icon"
              title="Toggle light/dark theme"
              aria-label="Toggle light or dark mode"
            >
              {colorMode === 'light' ? <Moon size={17} /> : <Sun size={17} />}
            </button>
          </div>
        </header>

        {/* Hero Banner */}
        <section className="m3-hero-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
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
                letterSpacing: '0.06em',
              }}
            >
              {wishlist.ownerName ? `${wishlist.ownerName}’s Wishlist` : 'Shared Gift Wishlist'}
            </span>

            {!isOwnerPreview && sortedItems.length > 0 && (
              <span
                style={{
                  fontSize: '12px',
                  fontWeight: 700,
                  padding: '4px 10px',
                  borderRadius: '99px',
                  background: 'var(--md-surface-container-highest)',
                }}
              >
                {counts.available} available • {counts.claimed} claimed
              </span>
            )}
          </div>

          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '10px',
            }}
          >
            <h1 style={{ fontSize: '24px', fontWeight: 800, lineHeight: 1.2 }}>{wishlist.title}</h1>

            {!isOwnerPreview && (
              <div>
                {savedWishlists.some((sw) => sw.shareId === shareId) ? (
                  <button
                    type="button"
                    onClick={() => router.push('/?tab=wishlist&sub=incoming')}
                    className="m3-btn-tonal"
                    style={{ height: '36px', fontSize: '12.5px' }}
                  >
                    <Check size={14} /> Saved in Wishlists — Open Hub →
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => {
                      const currentUrl =
                        typeof window !== 'undefined'
                          ? window.location.href
                          : `/w/${encodeURIComponent(shareId)}${
                              fallbackKey ? `?k=${encodeURIComponent(fallbackKey)}` : ''
                            }`;
                      void addSavedWishlist({
                        url: currentUrl,
                        title: wishlist.title,
                        ownerName: wishlist.ownerName || '',
                      }).then((saved) => {
                        if (saved) {
                          showToast('Saved to Shared With Me in your Gift Tracker!');
                        }
                      });
                    }}
                    className="m3-btn-filled"
                    style={{ height: '36px', fontSize: '12.5px' }}
                  >
                    <Bookmark size={14} /> Save to My Wishlists
                  </button>
                )}
              </div>
            )}
          </div>

          <p
            style={{
              fontSize: '13px',
              color: 'var(--md-on-surface-variant)',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              lineHeight: 1.45,
            }}
          >
            <Lock size={14} style={{ flexShrink: 0, color: 'var(--md-primary)' }} />
            <span>
              Claim items below so nobody duplicates gifts. Claims and notes sync live across this
              group and are <strong>strictly hidden from {ownerDisplay}</strong>.
            </span>
          </p>
        </section>

        {/* Owner Preview Mode Callout */}
        {isOwnerPreview && (
          <div
            className="m3-surface-card"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              border: '1px solid var(--md-primary)',
              background: 'var(--md-secondary-container)',
              color: 'var(--md-on-secondary-container)',
            }}
          >
            <EyeOff size={20} style={{ flexShrink: 0 }} />
            <div style={{ fontSize: '13px', lineHeight: 1.45 }}>
              <strong>Owner Preview Mode:</strong> You&rsquo;re signed in as the owner of this
              wishlist! You can verify which items, links, and notes appear here, while claim
              statuses remain hidden so your surprises aren&rsquo;t spoiled.
            </div>
          </div>
        )}

        {/* Filter Chips for Gift Givers */}
        {!isOwnerPreview && sortedItems.length > 0 && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            <button
              type="button"
              className="m3-chip"
              data-selected={filter === 'all'}
              onClick={() => setFilter('all')}
            >
              All Wishes ({counts.all})
            </button>
            <button
              type="button"
              className="m3-chip"
              data-selected={filter === 'available'}
              onClick={() => setFilter('available')}
            >
              Available ({counts.available})
            </button>
            {counts.mine > 0 && (
              <button
                type="button"
                className="m3-chip"
                data-selected={filter === 'mine'}
                onClick={() => setFilter('mine')}
              >
                Claimed by Me ({counts.mine})
              </button>
            )}
          </div>
        )}

        {/* Wishlist Checklist Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {displayedItems.length === 0 ? (
            <div
              className="m3-surface-card"
              style={{
                padding: '32px 20px',
                textAlign: 'center',
                color: 'var(--md-on-surface-variant)',
              }}
            >
              <p style={{ fontSize: '14.5px' }}>
                {sortedItems.length === 0
                  ? 'All wishes on this list have been received or none are added right now — check back before the next occasion!'
                  : filter === 'available'
                  ? 'Every wish on this list has been claimed!'
                  : 'You have not claimed any items yet.'}
              </p>
            </div>
          ) : (
            displayedItems.map((item) => {
              const claim = isOwnerPreview ? undefined : claims[item.id];
              const mine = isClaimMine(claim);
              const isResearching = claim?.status === 'researching';
              const claimedByOther = Boolean(claim && !mine && !isResearching);
              const isBusy = busyWishId === item.id;
              const isEditingNote = editingNoteWishId === item.id;
              const trackedGift = gifts.find(
                (g) => g.sourceWishlistShareId === shareId && g.sourceWishId === item.id
              );

              return (
                <div
                  key={item.id}
                  className="m3-surface-card"
                  style={{
                    padding: '16px',
                    opacity: claimedByOther ? 0.76 : 1,
                    border: mine
                      ? '1.5px solid var(--md-primary)'
                      : '1px solid var(--md-outline-variant)',
                    transition: 'all 180ms ease',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '10px',
                  }}
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
                    <div style={{ flex: '1 1 240px', minWidth: 0 }}>
                      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '8px' }}>
                        <h2
                          style={{
                            fontSize: '16px',
                            fontWeight: 700,
                            textDecoration: claimedByOther ? 'line-through' : 'none',
                            lineHeight: 1.35,
                          }}
                        >
                          {item.title}
                        </h2>

                        {claim && (
                          <span
                            style={{
                              fontSize: '11.5px',
                              fontWeight: 700,
                              padding: '3px 10px',
                              borderRadius: '99px',
                              background: claim.purchased
                                ? 'var(--md-success-container)'
                                : isResearching
                                ? 'var(--md-secondary-container)'
                                : mine
                                ? 'var(--md-primary-container)'
                                : 'var(--md-surface-container-highest)',
                              color: claim.purchased
                                ? 'var(--md-on-success-container)'
                                : isResearching
                                ? 'var(--md-on-secondary-container)'
                                : mine
                                ? 'var(--md-on-primary-container)'
                                : 'var(--md-on-surface-variant)',
                            }}
                          >
                            {claim.purchased
                              ? `✓ Purchased by ${mine ? `you (${claim.claimedBy})` : claim.claimedBy}`
                              : isResearching
                              ? `🔍 Researching: ${mine ? `you (${claim.claimedBy})` : claim.claimedBy}`
                              : `Reserved by ${mine ? `you (${claim.claimedBy})` : claim.claimedBy}`}
                          </span>
                        )}

                        {trackedGift && (
                          <span
                            style={{
                              fontSize: '11.5px',
                              fontWeight: 700,
                              padding: '3px 10px',
                              borderRadius: '99px',
                              background: 'var(--md-primary-container)',
                              color: 'var(--md-on-primary-container)',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                            }}
                            title="Saved in your Gift Tracker"
                          >
                            <Check size={12} strokeWidth={2.8} /> In Your Tracker
                          </span>
                        )}
                      </div>

                      {item.notes && (
                        <div
                          style={{
                            marginTop: '8px',
                            fontSize: '13px',
                            lineHeight: 1.45,
                            background: 'var(--md-surface-container-low)',
                            padding: '7px 11px',
                            borderRadius: '10px',
                            borderLeft: '3px solid var(--md-primary)',
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {item.notes}
                        </div>
                      )}

                      {claim?.giverNote && !isEditingNote && (
                        <div
                          style={{
                            marginTop: '8px',
                            fontSize: '12.5px',
                            background: 'var(--md-surface-container-highest)',
                            padding: '6px 10px',
                            borderRadius: '10px',
                            color: 'var(--md-on-surface)',
                          }}
                        >
                          💬 <strong>Note from {claim.claimedBy}:</strong> {claim.giverNote}
                        </div>
                      )}

                      {item.url && (
                        <div style={{ marginTop: '8px' }}>
                          <a
                            href={item.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                              color: 'var(--md-primary)',
                              fontWeight: 700,
                              fontSize: '13px',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '5px',
                            }}
                          >
                            <ExternalLink size={13} /> Open Product Link
                          </a>
                        </div>
                      )}
                    </div>

                    {/* Giver Action Controls */}
                    {!isOwnerPreview && (
                      <div
                        style={{
                          display: 'flex',
                          flexWrap: 'wrap',
                          alignItems: 'center',
                          gap: '6px',
                          flexShrink: 0,
                        }}
                      >
                        {/* Tracker button */}
                        {!trackedGift ? (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => setTrackerSheetWish(item)}
                            className="m3-btn-tonal"
                            style={{ height: '36px', fontSize: '12.5px' }}
                            title="Add to your personal gift tracker & budget"
                          >
                            <Sparkles size={14} /> + Track in Gifts
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => {
                              if (trackedGift.eventId) {
                                router.push(`/?tab=events&event=${trackedGift.eventId}`);
                              } else {
                                router.push('/?tab=ideas');
                              }
                            }}
                            className="m3-btn-text"
                            style={{ height: '34px', fontSize: '11.5px', opacity: 0.85 }}
                            title="Open in your tracker"
                          >
                            View in Tracker ↗
                          </button>
                        )}

                        {!claim ? (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => handleTapClaim(item.id)}
                            className="m3-btn-filled"
                            style={{ height: '36px', fontSize: '12.5px' }}
                          >
                            <Check size={14} /> Claim Gift
                          </button>
                        ) : isResearching ? (
                          <>
                            {mine ? (
                              <>
                                <button
                                  type="button"
                                  disabled={isBusy}
                                  onClick={() => handleTapClaim(item.id)}
                                  className="m3-btn-filled"
                                  style={{ height: '36px', fontSize: '12.5px' }}
                                  title="Promote to fully claimed so no one else gets it"
                                >
                                  <Check size={14} /> Fully Claim
                                </button>
                                <button
                                  type="button"
                                  disabled={isBusy}
                                  onClick={() => void handleTogglePurchased(claim)}
                                  className="m3-btn-tonal"
                                  style={{ height: '36px', fontSize: '12.5px' }}
                                >
                                  <ShoppingBag size={14} /> Mark Bought
                                </button>
                                <button
                                  type="button"
                                  disabled={isBusy}
                                  onClick={() => void handleUnclaim(claim, true)}
                                  className="m3-btn-text"
                                  style={{ height: '36px', fontSize: '12px' }}
                                >
                                  <RotateCcw size={13} /> Stop Researching
                                </button>
                              </>
                            ) : (
                              <button
                                type="button"
                                disabled={isBusy}
                                onClick={() => handleTapClaim(item.id)}
                                className="m3-btn-filled"
                                style={{ height: '36px', fontSize: '12.5px' }}
                              >
                                <Check size={14} /> Claim Gift
                              </button>
                            )}
                          </>
                        ) : mine ? (
                          <>
                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => void handleTogglePurchased(claim)}
                              className={claim.purchased ? 'm3-btn-filled' : 'm3-btn-tonal'}
                              style={{ height: '36px', fontSize: '12.5px' }}
                            >
                              <ShoppingBag size={14} />
                              {claim.purchased ? 'Purchased ✓' : 'Mark Purchased'}
                            </button>

                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => {
                                setEditingNoteWishId(isEditingNote ? null : item.id);
                                setGiverNoteDraft(claim.giverNote || '');
                              }}
                              className="m3-btn-tonal"
                              style={{ height: '36px', fontSize: '12.5px' }}
                              title="Leave a note visible only to other gift-givers"
                            >
                              <MessageSquarePlus size={14} />
                              {claim.giverNote ? 'Edit Note' : 'Group Note'}
                            </button>

                            <button
                              type="button"
                              disabled={isBusy}
                              onClick={() => void handleUnclaim(claim, true)}
                              className="m3-btn-text"
                              style={{ height: '36px', fontSize: '12px' }}
                            >
                              <RotateCcw size={13} /> Unclaim
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => void handleUnclaim(claim, false)}
                            className="m3-btn-text"
                            style={{ height: '34px', fontSize: '11.5px', opacity: 0.75 }}
                            title="Release claim if plans changed"
                          >
                            Release
                          </button>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Secret Giver Note Editor */}
                  {isEditingNote && claim && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        void handleSaveGiverNote(claim);
                      }}
                      style={{
                        paddingTop: '10px',
                        borderTop: '1px solid var(--md-outline-variant)',
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: '8px',
                        alignItems: 'center',
                      }}
                    >
                      <input
                        type="text"
                        value={giverNoteDraft}
                        onChange={(e) => setGiverNoteDraft(e.target.value)}
                        placeholder="Secret note for other givers (e.g., Splitting welcome, or Ordered Dec 20)..."
                        className="m3-input"
                        style={{ flex: '1 1 240px', height: '38px', fontSize: '12.5px' }}
                        autoFocus
                      />
                      <button
                        type="button"
                        onClick={() => setEditingNoteWishId(null)}
                        className="m3-btn-text"
                        style={{ height: '36px' }}
                      >
                        Cancel
                      </button>
                      <button type="submit" className="m3-btn-filled" style={{ height: '36px' }}>
                        Save Note
                      </button>
                    </form>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* First-Time Giver Name Modal */}
      {pendingClaimWishId && (
        <div
          className="m3-scrim"
          onClick={() => setPendingClaimWishId(null)}
          role="dialog"
          aria-modal="true"
        >
          <form
            className="m3-sheet"
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => {
              e.preventDefault();
              if (!pendingNameInput.trim()) return;
              const clean = pendingNameInput.trim();
              saveGiverName(clean);
              const targetWish = pendingClaimWishId;
              setPendingClaimWishId(null);
              void executeClaim(targetWish, clean);
            }}
            style={{ maxWidth: '420px', display: 'flex', flexDirection: 'column', gap: '14px' }}
          >
            <div className="m3-drag-handle" />
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h2 style={{ fontSize: '19px', fontWeight: 800 }}>Who&rsquo;s claiming this gift?</h2>
              <button
                type="button"
                onClick={() => setPendingClaimWishId(null)}
                className="m3-btn-icon"
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', lineHeight: 1.45 }}>
              Enter your name so others in the group know it&rsquo;s taken care of (hidden from{' '}
              {ownerDisplay}).
            </p>
            <input
              type="text"
              required
              autoFocus
              value={pendingNameInput}
              onChange={(e) => setPendingNameInput(e.target.value)}
              placeholder="Your name (e.g., Aunt Sarah, Alex)..."
              className="m3-input"
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button
                type="button"
                onClick={() => setPendingClaimWishId(null)}
                className="m3-btn-text"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!pendingNameInput.trim()}
                className="m3-btn-filled"
              >
                <Check size={15} /> Claim Gift
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Add To Personal Tracker Bottom Sheet */}
      {trackerSheetWish && wishlist && (
        <AddToTrackerSheet
          open={Boolean(trackerSheetWish)}
          onClose={() => setTrackerSheetWish(null)}
          wish={trackerSheetWish}
          wishlistOwnerName={wishlist.ownerName}
          shareId={shareId}
          fallbackKey={fallbackKey}
          giverName={giverName}
          onGiverNameChange={saveGiverName}
          claimerToken={claimerToken}
          onAddedToTracker={(newGift, status) => {
            showToast(
              status === 'reserved'
                ? `Added "${newGift.title}" to tracker & reserved on wishlist!`
                : `Added "${newGift.title}" to tracker as researching!`
            );
          }}
        />
      )}

      {/* Toast Notification */}
      {toast && (
        <div className="m3-snackbar" role="status" aria-live="polite">
          <span>{toast}</span>
          <button
            type="button"
            onClick={() => setToast(null)}
            style={{ opacity: 0.7, display: 'inline-flex' }}
            aria-label="Dismiss"
          >
            <X size={15} />
          </button>
        </div>
      )}
    </div>
  );
}
