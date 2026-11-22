'use client';

import React, { useMemo, useState } from 'react';
import {
  CalendarHeart,
  Lightbulb,
  Plus,
  Settings,
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
    const activeEventGifts =
      selectedEventId === 'year:2026'
        ? gifts.filter((g) => g.eventId?.startsWith('2026-')).length
        : gifts.filter((g) => g.eventId === selectedEventId).length;
    const ideasCount = gifts.filter((g) => g.eventId === null).length;
    const wantedCount = wishes.filter((w) => !w.received).length;
    return {
      events: activeEventGifts,
      ideas: ideasCount,
      people: people.length,
      wishlist: wantedCount,
    };
  }, [gifts, people, wishes, selectedEventId]);

  const currentHeading = useMemo(() => {
    if (activeTab === 'events') {
      if (selectedEventId === 'year:2026') return '2026 Occasions';
      return events.find((e) => e.id === selectedEventId)?.name || 'Events & Occasions';
    }
    if (activeTab === 'ideas') return 'Gift Ideas & Stash';
    if (activeTab === 'people') return 'People & Sizes';
    return 'My Wishlist';
  }, [activeTab, selectedEventId, events]);

  const openNewGiftModal = (defaults?: {
    personId?: string | null;
    eventId?: string | null;
  }) => {
    setEditingGift(null);
    setComposerDefaults({
      personId: defaults?.personId ?? null,
      eventId:
        defaults?.eventId !== undefined
          ? defaults.eventId
          : activeTab === 'events'
          ? selectedEventId === 'year:2026'
            ? '2026-christmas'
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
      label: 'My Wishlist',
      shortLabel: 'Me',
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
            <div style={{ fontSize: '11px', color: 'var(--md-on-surface-variant)' }}>
              Material You Expressive
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

          {activeTab === 'wishlist' && <WishlistView />}
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
