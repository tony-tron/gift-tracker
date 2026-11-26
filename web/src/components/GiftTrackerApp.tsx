'use client';

import React, { useMemo, useState } from 'react';
import {
  CalendarHeart,
  Check,
  Cloud,
  Lightbulb,
  LogIn,
  Plus,
  Settings,
  Smartphone,
  Sparkles,
  Star,
  Users,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { EventsView } from '@/components/views/EventsView';
import { IdeasView } from '@/components/views/IdeasView';
import { PeopleView } from '@/components/views/PeopleView';
import { WishlistView } from '@/components/views/WishlistView';
import {
  GiftComposerSheet,
  type GiftComposerDefaults,
} from '@/components/modals/GiftComposerSheet';
import { MoveToEventSheet } from '@/components/modals/MoveToEventSheet';
import { PersonEditorSheet } from '@/components/modals/PersonEditorSheet';
import { SettingsSheet } from '@/components/modals/SettingsSheet';
import type { GiftItem, NavTab, Person } from '@/lib/types';

interface GiftTrackerAppProps {
  initialSharedDraft?: {
    title?: string;
    url?: string;
    store?: string;
    cost?: number | null;
  } | null;
}

export function GiftTrackerApp({ initialSharedDraft }: GiftTrackerAppProps) {
  const {
    people,
    events,
    gifts,
    wishes,
    activeTab,
    setActiveTab,
    selectedEventId,
    syncStatus,
    syncError,
    firebaseConfig,
    firebaseUser,
    incomingPairToken,
    dismissIncomingPair,
    loginGoogle,
    loginAnonymousSync,
    snackbar,
    dismissSnackbar,
  } = useGiftStore();

  const hasSharedDraft = Boolean(
    initialSharedDraft && (initialSharedDraft.url || initialSharedDraft.title)
  );

  const [composerOpen, setComposerOpen] = useState<boolean>(() => {
    if (hasSharedDraft) return true;
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('action') === 'new-idea';
    }
    return false;
  });

  const [editingGift, setEditingGift] = useState<GiftItem | null>(null);
  const [composerDefaults, setComposerDefaults] = useState<GiftComposerDefaults>(() => {
    if (hasSharedDraft && initialSharedDraft) {
      return {
        title: initialSharedDraft.title || '',
        url: initialSharedDraft.url || '',
        store: initialSharedDraft.store || '',
        cost: initialSharedDraft.cost ?? null,
        eventId: null,
      };
    }
    return { eventId: null };
  });

  const [movingGift, setMovingGift] = useState<GiftItem | null>(null);
  const [personSheetOpen, setPersonSheetOpen] = useState(false);
  const [editingPerson, setEditingPerson] = useState<Person | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const counts = useMemo(() => {
    const isAllActive = selectedEventId === 'all:active' || selectedEventId === 'year:2026';
    const activeEventGifts = isAllActive
      ? (() => {
          const activeIds = new Set(events.filter((e) => !e.isArchived).map((e) => e.id));
          return gifts.filter((g) => g.eventId && activeIds.has(g.eventId)).length;
        })()
      : gifts.filter((g) => g.eventId === selectedEventId).length;
    const ideasCount = gifts.filter((g) => g.eventId === null).length;
    const wantedCount = wishes.filter((w) => !w.received).length;
    return {
      events: activeEventGifts,
      ideas: ideasCount,
      people: people.length,
      wishlist: wantedCount,
    };
  }, [gifts, events, people, wishes, selectedEventId]);

  const currentHeading = useMemo(() => {
    if (activeTab === 'events') {
      if (selectedEventId === 'all:active' || selectedEventId === 'year:2026') {
        return 'All Active Occasions';
      }
      return events.find((e) => e.id === selectedEventId)?.name || 'Events & Occasions';
    }
    if (activeTab === 'ideas') return 'Gift Ideas & Stash';
    if (activeTab === 'people') return 'People & Sizes';
    return 'Wishlists';
  }, [activeTab, selectedEventId, events]);

  const openNewGiftModal = (defaults?: {
    personId?: string | null;
    eventId?: string | null;
    url?: string;
  }) => {
    setEditingGift(null);
    setComposerDefaults({
      personId: defaults?.personId ?? null,
      url: defaults?.url,
      eventId:
        defaults?.eventId !== undefined
          ? defaults.eventId
          : activeTab === 'events'
          ? selectedEventId === 'all:active' || selectedEventId === 'year:2026'
            ? events.find((e) => !e.isArchived)?.id || '2026-christmas'
            : selectedEventId
          : null,
    });
    setComposerOpen(true);
  };

  const openEditGiftModal = (gift: GiftItem) => {
    setEditingGift(gift);
    setComposerOpen(true);
  };

  const navItems: Array<{
    id: NavTab;
    label: string;
    shortLabel: string;
    count: number;
    icon: React.ReactNode;
  }> = [
    {
      id: 'events',
      label: 'Occasions',
      shortLabel: 'Events',
      count: counts.events,
      icon: <CalendarHeart size={20} />,
    },
    {
      id: 'ideas',
      label: 'Gift Ideas',
      shortLabel: 'Ideas',
      count: counts.ideas,
      icon: <Lightbulb size={20} />,
    },
    {
      id: 'people',
      label: 'People & Sizes',
      shortLabel: 'People',
      count: counts.people,
      icon: <Users size={20} />,
    },
    {
      id: 'wishlist',
      label: 'Wishlists',
      shortLabel: 'Lists',
      count: counts.wishlist,
      icon: <Star size={20} />,
    },
  ];

  return (
    <div className="app-shell">
      {/* ADAPTIVE NAVIGATION RAIL / EXPANDED DRAWER */}
      <aside className="m3-nav-rail" aria-label="Primary Navigation">
        <div className="rail-brand">
          <div
            className="m3-avatar"
            data-shape="clover"
            data-tone="terracotta"
            style={{ width: '40px', height: '40px' }}
          >
            <Sparkles size={18} />
          </div>
          <div className="rail-brand-text">
            <div style={{ fontWeight: 800, fontSize: '16px', letterSpacing: '-0.02em' }}>
              Gift Tracker
            </div>
          </div>
        </div>

        <button
          type="button"
          id="rail-new-gift-fab"
          onClick={() => openNewGiftModal()}
          className="rail-fab"
          title="Add Gift or Idea"
        >
          <Plus size={22} strokeWidth={2.6} />
          <span className="rail-fab-label">New Gift</span>
        </button>

        <nav className="rail-destinations">
          {navItems.map((item) => (
            <button
              key={item.id}
              id={`nav-rail-${item.id}`}
              type="button"
              data-active={activeTab === item.id}
              onClick={() => setActiveTab(item.id)}
              className="rail-item"
            >
              <span className="rail-icon-pill">{item.icon}</span>
              <span>{item.label}</span>
              <span className="rail-badge">{item.count}</span>
            </button>
          ))}
        </nav>

        <div style={{ marginTop: 'auto', width: '100%' }}>
          <button
            type="button"
            id="rail-settings-btn"
            onClick={() => setSettingsOpen(true)}
            className="rail-item"
            style={{ width: '100%' }}
            title="Settings"
          >
            <span className="rail-icon-pill" style={{ position: 'relative' }}>
              <Settings size={19} />
              {syncStatus === 'synced' && (
                <span
                  aria-hidden="true"
                  style={{
                    position: 'absolute',
                    top: '4px',
                    right: '12px',
                    width: '7px',
                    height: '7px',
                    borderRadius: '99px',
                    background: 'var(--md-success)',
                  }}
                />
              )}
            </span>
            <span>Settings</span>
          </button>
        </div>
      </aside>

      {/* MAIN CONTENT WORKSPACE */}
      <div className="m3-workspace">
        <header className="m3-page-header">
          <div>
            <h1 className="m3-headline-xl">{currentHeading}</h1>
          </div>

          <button
            type="button"
            id="header-settings-btn"
            onClick={() => setSettingsOpen(true)}
            className="m3-btn-icon"
            aria-label="Settings"
            title={syncStatus === 'synced' ? 'Settings (Cloud Synced)' : 'Settings'}
            style={{ position: 'relative' }}
          >
            <Settings size={20} />
            {syncStatus === 'synced' && (
              <span
                aria-hidden="true"
                style={{
                  position: 'absolute',
                  top: '7px',
                  right: '7px',
                  width: '8px',
                  height: '8px',
                  borderRadius: '99px',
                  background: 'var(--md-success)',
                  border: '1.5px solid var(--md-surface-container-high)',
                }}
              />
            )}
          </button>
        </header>

        <main className="m3-content-container">
          {activeTab === 'events' && (
            <EventsView
              onOpenNewGift={(defs) => openNewGiftModal(defs)}
              onEditGift={openEditGiftModal}
            />
          )}

          {activeTab === 'ideas' && (
            <IdeasView
              onOpenNewIdea={(defs) => openNewGiftModal(defs)}
              onEditGift={openEditGiftModal}
              onOpenMoveModal={(gift) => setMovingGift(gift)}
            />
          )}

          {activeTab === 'people' && (
            <PeopleView
              onOpenPersonEditor={(person) => {
                setEditingPerson(person || null);
                setPersonSheetOpen(true);
              }}
              onOpenNewGiftForPerson={(personId) =>
                openNewGiftModal({ personId, eventId: null })
              }
              onEditGift={openEditGiftModal}
            />
          )}

          {activeTab === 'wishlist' && (
            <WishlistView onOpenNewGift={(defs) => openNewGiftModal(defs)} />
          )}
        </main>
      </div>

      {/* M3 EXPRESSIVE FLOATING BOTTOM TOOLBAR + PAIRED VIBRANT FAB */}
      <div className="m3-bottom-dock">
        <nav className="m3-floating-toolbar" aria-label="Bottom Navigation">
          {navItems.map((item) => (
            <button
              key={item.id}
              id={`nav-bottom-${item.id}`}
              type="button"
              data-active={activeTab === item.id}
              onClick={() => setActiveTab(item.id)}
              className="m3-toolbar-tab"
            >
              {item.icon}
              <span className="tab-label">{item.shortLabel}</span>
            </button>
          ))}
        </nav>

        <button
          type="button"
          id="mobile-vibrant-fab"
          onClick={() => openNewGiftModal()}
          className="m3-fab-vibrant"
          aria-label="Add Gift or Idea"
        >
          <Plus size={24} strokeWidth={2.8} />
        </button>
      </div>

      {/* SHEETS & DIALOGS */}
      <GiftComposerSheet
        open={composerOpen}
        onClose={() => setComposerOpen(false)}
        editingGift={editingGift}
        initialDefaults={composerDefaults}
      />

      <MoveToEventSheet
        gift={movingGift}
        onClose={() => setMovingGift(null)}
      />

      <PersonEditorSheet
        open={personSheetOpen}
        onClose={() => setPersonSheetOpen(false)}
        editingPerson={editingPerson}
      />

      <SettingsSheet
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
      />

      {incomingPairToken && firebaseConfig && (
        <div
          className="m3-scrim"
          onClick={dismissIncomingPair}
          role="dialog"
          aria-modal="true"
          aria-label="Cloud Sync Setup Ready"
        >
          <div
            className="m3-sheet"
            onClick={(e) => e.stopPropagation()}
            style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}
          >
            <div className="m3-drag-handle" />
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
                  data-shape="squircle"
                  data-tone="teal"
                  style={{ width: '40px', height: '40px', flexShrink: 0 }}
                >
                  <Cloud size={19} />
                </div>
                <div>
                  <span
                    style={{
                      fontSize: '11px',
                      fontWeight: 700,
                      color: 'var(--md-primary)',
                      textTransform: 'uppercase',
                      letterSpacing: '0.06em',
                    }}
                  >
                    Cloud Sync Paired
                  </span>
                  <h2 style={{ fontSize: '19px', fontWeight: 800, marginTop: '1px' }}>
                    Connected to {firebaseConfig.projectId}
                  </h2>
                </div>
              </div>
              <button
                type="button"
                onClick={dismissIncomingPair}
                className="m3-btn-icon"
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', lineHeight: 1.5 }}>
              Firebase Cloud Sync is now configured on this browser. Sign in with your Google
              account to sync across your devices — each Google account gets its own private,
              isolated Gift Tracker workspace.
            </p>

            {firebaseUser ? (
              <div
                style={{
                  background: 'var(--md-success-container)',
                  color: 'var(--md-on-success-container)',
                  padding: '12px 14px',
                  borderRadius: '16px',
                  fontSize: '13px',
                  fontWeight: 700,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                <Check size={16} />
                <span>
                  Signed in as {firebaseUser.email || firebaseUser.displayName || 'Cloud User'}
                </span>
              </div>
            ) : (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                <button
                  type="button"
                  onClick={async () => {
                    try {
                      await loginGoogle();
                      dismissIncomingPair();
                    } catch {
                      // Error shown below
                    }
                  }}
                  className="m3-btn-filled"
                  style={{ flex: '1 1 200px', justifyContent: 'center' }}
                >
                  <LogIn size={16} /> Sign in with Google
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    try {
                      await loginAnonymousSync();
                      dismissIncomingPair();
                    } catch {
                      // Error shown below
                    }
                  }}
                  className="m3-btn-tonal"
                >
                  Anonymous Sync
                </button>
              </div>
            )}

            {syncError && (
              <p style={{ fontSize: '12px', color: 'var(--md-danger)' }}>{syncError}</p>
            )}

            <div
              style={{
                borderTop: '1px solid var(--md-outline-variant)',
                paddingTop: '12px',
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '10px',
              }}
            >
              <a
                href={`gifttracker://pair?cfg=${encodeURIComponent(incomingPairToken)}`}
                className="m3-btn-tonal"
                style={{ textDecoration: 'none', fontSize: '12.5px' }}
              >
                <Smartphone size={15} /> Open in Android App
              </a>

              <button type="button" onClick={dismissIncomingPair} className="m3-btn-text">
                {firebaseUser ? 'Done' : 'Later'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* EXPRESSIVE SNACKBAR */}
      {snackbar && (
        <div className="m3-snackbar" role="status" aria-live="polite">
          <span>{snackbar.text}</span>
          {snackbar.actionLabel && snackbar.onAction && (
            <button
              type="button"
              onClick={() => {
                snackbar.onAction?.();
                dismissSnackbar();
              }}
              style={{
                color: 'var(--md-primary-container)',
                background: 'var(--md-on-primary-container)',
                padding: '4px 12px',
                borderRadius: '99px',
                fontSize: '12px',
                fontWeight: 800,
              }}
            >
              {snackbar.actionLabel}
            </button>
          )}
          <button
            type="button"
            onClick={dismissSnackbar}
            style={{ opacity: 0.7, display: 'inline-flex' }}
            aria-label="Dismiss notification"
          >
            <X size={15} />
          </button>
        </div>
      )}
    </div>
  );
}
