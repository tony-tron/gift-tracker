'use client';

import React, { useEffect, useMemo, useState } from 'react';
import {
  Archive,
  ArchiveRestore,
  Bookmark,
  CalendarPlus,
  Check,
  ExternalLink,
  Lightbulb,
  Package,
  Pencil,
  Plus,
  Search,
  Sparkles,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { ExpressiveProgress } from '@/components/ui/ExpressiveProgress';
import type { GiftItem, OccasionType } from '@/lib/types';

interface EventsViewProps {
  onOpenNewGift: (defaults?: { personId?: string | null; eventId?: string | null }) => void;
  onEditGift: (gift: GiftItem) => void;
}

export function EventsView({ onOpenNewGift, onEditGift }: EventsViewProps) {
  const {
    people,
    events,
    gifts,
    selectedEventId,
    setSelectedEventId,
    toggleGiftStatus,
    moveGiftToIdeas,
    moveGiftToEvent,
    addEvent,
    updateEvent,
    setActiveTab,
    setSelectedPersonId,
  } = useGiftStore();

  const [personFilter, setPersonFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'to-buy' | 'to-wrap'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [showArchivedEvents, setShowArchivedEvents] = useState(false);
  const [creatingEvent, setCreatingEvent] = useState(false);
  const [newYear, setNewYear] = useState(2026);
  const [newOccasion, setNewOccasion] = useState<OccasionType>('Christmas');
  const [newCustomTitle, setNewCustomTitle] = useState('');

  useEffect(() => {
    setNewYear(new Date().getFullYear());
  }, []);

  const personMap = useMemo(() => {
    const map = new Map(people.map((p) => [p.id, p]));
    return map;
  }, [people]);

  const activeEvents = useMemo(() => events.filter((e) => !e.isArchived), [events]);
  const archivedEvents = useMemo(() => events.filter((e) => e.isArchived), [events]);

  // Support unified "All Active" view ('all:active' / legacy 'year:2026') OR individual event ID
  const isAllActiveMode = selectedEventId === 'all:active' || selectedEventId === 'year:2026';
  const currentEvent = useMemo(
    () => events.find((e) => e.id === selectedEventId) || events[0],
    [events, selectedEventId]
  );

  const eventGifts = useMemo(() => {
    if (isAllActiveMode) {
      const activeIds = new Set(activeEvents.map((e) => e.id));
      return gifts.filter((g) => g.eventId && activeIds.has(g.eventId));
    }
    return gifts.filter((g) => g.eventId === selectedEventId);
  }, [gifts, activeEvents, selectedEventId, isAllActiveMode]);

  const filteredGifts = useMemo(() => {
    return eventGifts.filter((g) => {
      if (personFilter === 'unassigned' && g.personId !== null) return false;
      if (personFilter !== 'all' && personFilter !== 'unassigned' && g.personId !== personFilter) {
        return false;
      }
      if (statusFilter === 'to-buy' && g.bought) return false;
      if (statusFilter === 'to-wrap' && g.wrapped) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const pName = g.personId ? personMap.get(g.personId)?.name.toLowerCase() || '' : '';
        return (
          g.title.toLowerCase().includes(q) ||
          g.store.toLowerCase().includes(q) ||
          pName.includes(q)
        );
      }
      return true;
    });
  }, [eventGifts, personFilter, statusFilter, searchQuery, personMap]);

  // Totals & Budget Progress
  const totalBudget = useMemo(
    () => people.reduce((acc, p) => acc + (p.budget || 0), 0),
    [people]
  );

  const stats = useMemo(() => {
    const spent = eventGifts.reduce((sum, g) => sum + (g.cost || 0), 0);
    const boughtCount = eventGifts.filter((g) => g.bought).length;
    const receivedCount = eventGifts.filter((g) => g.received).length;
    const wrappedCount = eventGifts.filter((g) => g.wrapped).length;
    return {
      spent,
      totalItems: eventGifts.length,
      boughtCount,
      receivedCount,
      wrappedCount,
    };
  }, [eventGifts]);

  // Per-person budget standing for the Supporting Pane
  const recipientBreakdown = useMemo(() => {
    const spentByPerson = new Map<string, number>();
    for (const g of eventGifts) {
      if (g.personId) {
        spentByPerson.set(g.personId, (spentByPerson.get(g.personId) || 0) + (g.cost || 0));
      }
    }
    return people
      .filter((p) => p.budget > 0 || (spentByPerson.get(p.id) || 0) > 0)
      .map((p) => {
        const spent = spentByPerson.get(p.id) || 0;
        const ideasCount = gifts.filter((g) => g.eventId === null && g.personId === p.id).length;
        return { person: p, spent, ideasCount };
      });
  }, [people, eventGifts, gifts]);

  // Suggestions from Ideas Backlog for people who haven't hit budget yet
  const suggestedIdeas = useMemo(() => {
    const targetEvId = isAllActiveMode ? activeEvents[0]?.id || '2026-christmas' : selectedEventId;
    if (currentEvent?.isArchived && !isAllActiveMode) return [];
    return gifts
      .filter((g) => g.eventId === null && g.personId)
      .slice(0, 5)
      .map((idea) => ({
        idea,
        person: idea.personId ? personMap.get(idea.personId) : undefined,
        targetEvId,
      }));
  }, [gifts, isAllActiveMode, activeEvents, selectedEventId, currentEvent, personMap]);

  const handleCreateEvent = (e: React.FormEvent) => {
    e.preventDefault();
    const emojiMap: Record<OccasionType, string> = {
      Christmas: '🎄',
      Birthday: '🎂',
      Anniversary: '💝',
      Other: '✨',
    };
    const title =
      newCustomTitle.trim() ||
      `${newYear} ${newOccasion === 'Birthday' ? 'Birthdays' : newOccasion}`;
    addEvent({
      name: title,
      year: newYear,
      occasion: newOccasion,
      emoji: emojiMap[newOccasion],
      isActive: true,
      isArchived: false,
    });
    setNewCustomTitle('');
    setCreatingEvent(false);
  };

  const budgetPct = totalBudget > 0 ? (stats.spent / totalBudget) * 100 : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
      {/* Occasion Switcher Chips */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div className="m3-chip-row" role="tablist" aria-label="Gift Occasions">
          {activeEvents.map((ev) => (
            <button
              key={ev.id}
              type="button"
              role="tab"
              aria-selected={selectedEventId === ev.id}
              className="m3-chip"
              data-selected={selectedEventId === ev.id}
              onClick={() => {
                setSelectedEventId(ev.id);
                setPersonFilter('all');
              }}
            >
              <span>{ev.emoji}</span>
              <span>{ev.name}</span>
            </button>
          ))}

          <button
            type="button"
            className="m3-chip"
            data-selected={isAllActiveMode}
            onClick={() => {
              setSelectedEventId('all:active');
              setPersonFilter('all');
            }}
          >
            <span>🗓️</span>
            <span>All Active</span>
          </button>

          <button
            type="button"
            className="m3-chip"
            data-selected={showArchivedEvents || Boolean(currentEvent?.isArchived && !isAllActiveMode)}
            onClick={() => setShowArchivedEvents((v) => !v)}
          >
            <Archive size={14} />
            <span>Archived</span>
          </button>

          <button
            type="button"
            className="m3-chip"
            data-selected={creatingEvent}
            onClick={() => setCreatingEvent((v) => !v)}
          >
            <CalendarPlus size={14} />
            <span>New Occasion</span>
          </button>
        </div>

        {/* Archived Occasions Drawer */}
        {(showArchivedEvents || (currentEvent?.isArchived && !isAllActiveMode)) && (
          <div className="m3-chip-row" style={{ paddingLeft: '4px' }}>
            {archivedEvents.map((ev) => (
              <button
                key={ev.id}
                type="button"
                className="m3-chip"
                data-selected={selectedEventId === ev.id}
                onClick={() => {
                  setSelectedEventId(ev.id);
                  setPersonFilter('all');
                }}
                style={{ height: '34px', fontSize: '12px' }}
              >
                <span>{ev.emoji}</span>
                <span>{ev.name}</span>
              </button>
            ))}
          </div>
        )}

        {/* Inline New Event Creator */}
        {creatingEvent && (
          <form
            onSubmit={handleCreateEvent}
            className="m3-surface-card"
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '10px',
              alignItems: 'flex-end',
            }}
          >
            <div style={{ width: '96px' }}>
              <label className="m3-field-label">Year</label>
              <input
                type="number"
                value={newYear}
                onChange={(e) => setNewYear(Number(e.target.value) || 2026)}
                className="m3-input"
              />
            </div>
            <div style={{ minWidth: '150px' }}>
              <label className="m3-field-label">Occasion</label>
              <select
                value={newOccasion}
                onChange={(e) => setNewOccasion(e.target.value as OccasionType)}
                className="m3-select"
              >
                <option value="Christmas">🎄 Christmas</option>
                <option value="Birthday">🎂 Birthdays</option>
                <option value="Anniversary">💝 Anniversary</option>
                <option value="Other">✨ Other</option>
              </select>
            </div>
            <div style={{ flex: 1, minWidth: '180px' }}>
              <label className="m3-field-label">Custom Page Name (Optional)</label>
              <input
                type="text"
                value={newCustomTitle}
                onChange={(e) => setNewCustomTitle(e.target.value)}
                placeholder={`e.g., ${newYear} ${newOccasion}`}
                className="m3-input"
              />
            </div>
            <button type="submit" className="m3-btn-filled">
              Create Page
            </button>
          </form>
        )}
      </div>

      {/* Canonical Adaptive Supporting-Pane Layout */}
      <div className="canonical-supporting-layout">
        {/* LEFT / MAIN PANE: Hero Budget Card + Search/Filters + Gift Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
          {/* Expressive Hero Summary Card */}
          <div className="m3-hero-card">
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'flex-end',
                justifyContent: 'space-between',
                gap: '12px',
                marginBottom: '12px',
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
                  {isAllActiveMode
                    ? 'All Active Occasions'
                    : currentEvent?.isArchived
                    ? 'Archived'
                    : 'Active Occasion'}
                </span>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '10px', marginTop: '2px' }}>
                  <span className="m3-display-num" style={{ fontSize: '34px', lineHeight: 1.05 }}>
                    ${stats.spent.toLocaleString()}
                  </span>
                  {(!currentEvent?.isArchived || currentEvent?.occasion === 'Christmas') && (
                    <span style={{ fontSize: '14px', color: 'var(--md-on-surface-variant)' }}>
                      of ${totalBudget.toLocaleString()} target budget
                    </span>
                  )}
                </div>
              </div>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                {!isAllActiveMode && currentEvent && (
                  <button
                    type="button"
                    onClick={() =>
                      updateEvent(currentEvent.id, { isArchived: !currentEvent.isArchived })
                    }
                    className="m3-btn-tonal"
                    title={currentEvent.isArchived ? 'Restore to active occasions' : 'Move to Archived'}
                  >
                    {currentEvent.isArchived ? (
                      <>
                        <ArchiveRestore size={16} /> Unarchive
                      </>
                    ) : (
                      <>
                        <Archive size={16} /> Archive
                      </>
                    )}
                  </button>
                )}

                <button
                  type="button"
                  onClick={() =>
                    onOpenNewGift({
                      eventId: isAllActiveMode
                        ? activeEvents[0]?.id || '2026-christmas'
                        : currentEvent?.id || activeEvents[0]?.id || '2026-christmas',
                      personId: personFilter !== 'all' && personFilter !== 'unassigned' ? personFilter : null,
                    })
                  }
                  className="m3-btn-filled"
                >
                  <Plus size={16} /> Add Gift Here
                </button>
              </div>
            </div>

            <ExpressiveProgress
              value={budgetPct}
              overBudget={stats.spent > totalBudget && totalBudget > 0}
            />

            {/* Fulfillment Stat Pills */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: '8px',
                marginTop: '14px',
              }}
            >
              <div
                style={{
                  background: 'var(--md-surface-container)',
                  borderRadius: '16px',
                  padding: '10px 12px',
                }}
              >
                <span style={{ fontSize: '11.5px', color: 'var(--md-on-surface-variant)', display: 'block' }}>
                  Bought
                </span>
                <strong className="m3-display-num" style={{ fontSize: '17px', color: 'var(--md-success)' }}>
                  {stats.boughtCount} / {stats.totalItems}
                </strong>
              </div>
              <div
                style={{
                  background: 'var(--md-surface-container)',
                  borderRadius: '16px',
                  padding: '10px 12px',
                }}
              >
                <span style={{ fontSize: '11.5px', color: 'var(--md-on-surface-variant)', display: 'block' }}>
                  Arrived
                </span>
                <strong className="m3-display-num" style={{ fontSize: '17px', color: 'var(--md-secondary)' }}>
                  {stats.receivedCount} / {stats.totalItems}
                </strong>
              </div>
              <div
                style={{
                  background: 'var(--md-surface-container)',
                  borderRadius: '16px',
                  padding: '10px 12px',
                }}
              >
                <span style={{ fontSize: '11.5px', color: 'var(--md-on-surface-variant)', display: 'block' }}>
                  Wrapped
                </span>
                <strong className="m3-display-num" style={{ fontSize: '17px', color: 'var(--md-tertiary)' }}>
                  {stats.wrappedCount} / {stats.totalItems}
                </strong>
              </div>
            </div>
          </div>

          {/* Search & Status / Recipient Filters */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
              <div style={{ position: 'relative', flex: '1 1 200px' }}>
                <Search
                  size={16}
                  style={{
                    position: 'absolute',
                    left: '14px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    color: 'var(--md-on-surface-variant)',
                  }}
                />
                <input
                  type="search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search gifts, stores, or recipients..."
                  className="m3-input"
                  style={{ paddingLeft: '38px', borderRadius: '999px', height: '40px' }}
                />
              </div>

              <div style={{ display: 'flex', gap: '6px' }}>
                {(
                  [
                    { id: 'all', label: 'All' },
                    { id: 'to-buy', label: 'Needs Buying' },
                    { id: 'to-wrap', label: 'Needs Wrapping' },
                  ] as const
                ).map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    onClick={() => setStatusFilter(f.id)}
                    className="m3-chip"
                    data-selected={statusFilter === f.id}
                    style={{ height: '38px', fontSize: '12.5px' }}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            </div>

            {personFilter !== 'all' && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '12.5px', color: 'var(--md-on-surface-variant)' }}>
                  Filtered by:{' '}
                  <strong>
                    {personFilter === 'unassigned'
                      ? 'General Pool'
                      : personMap.get(personFilter)?.name}
                  </strong>
                </span>
                <button
                  type="button"
                  onClick={() => setPersonFilter('all')}
                  style={{ fontSize: '12.5px', color: 'var(--md-primary)', fontWeight: 700 }}
                >
                  Clear filter
                </button>
              </div>
            )}
          </div>

          {/* Gift Checklist Cards */}
          {filteredGifts.length === 0 ? (
            <div
              className="m3-surface-card-low"
              style={{ textAlign: 'center', padding: '38px 20px' }}
            >
              <Sparkles
                size={28}
                style={{ color: 'var(--md-primary)', margin: '0 auto 10px' }}
              />
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>
                No gifts match this view yet
              </h3>
              <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', marginBottom: '16px' }}>
                Promote ideas from your Gift Ideas backlog or add a gift directly.
              </p>
              <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  onClick={() => setActiveTab('ideas')}
                  className="m3-btn-tonal"
                >
                  <Lightbulb size={15} /> Browse Ideas Backlog
                </button>
                <button
                  type="button"
                  onClick={() =>
                    onOpenNewGift({
                      eventId: isAllActiveMode
                        ? activeEvents[0]?.id || '2026-christmas'
                        : currentEvent?.id || activeEvents[0]?.id || '2026-christmas',
                    })
                  }
                  className="m3-btn-filled"
                >
                  <Plus size={15} /> Add Gift
                </button>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {filteredGifts.map((gift) => {
                const person = gift.personId ? personMap.get(gift.personId) : undefined;
                const evInfo = isAllActiveMode
                  ? events.find((e) => e.id === gift.eventId)
                  : undefined;

                return (
                  <article key={gift.id} className="m3-surface-card">
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: '12px',
                      }}
                    >
                      <div style={{ display: 'flex', gap: '12px', minWidth: 0, flex: 1 }}>
                        <button
                          type="button"
                          onClick={() => {
                            if (person) {
                              setSelectedPersonId(person.id);
                              setActiveTab('people');
                            }
                          }}
                          className="m3-avatar"
                          data-shape={person?.avatarShape || 'pill'}
                          data-tone={person?.avatarTone || 'ochre'}
                          title={person ? `Open ${person.name}'s profile & sizes` : 'Unassigned'}
                        >
                          {(person?.name || '?').slice(0, 2).toUpperCase()}
                        </button>

                        <div style={{ minWidth: 0, flex: 1 }}>
                          <div
                            style={{
                              display: 'flex',
                              flexWrap: 'wrap',
                              alignItems: 'center',
                              gap: '6px',
                            }}
                          >
                            <span
                              style={{
                                fontSize: '12px',
                                fontWeight: 700,
                                color: 'var(--md-primary)',
                              }}
                            >
                              {person?.name || 'Not sure who'}
                            </span>
                            {gift.store && (
                              <span style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
                                • {gift.store}
                              </span>
                            )}
                            {evInfo && (
                              <span
                                style={{
                                  fontSize: '11px',
                                  padding: '1px 8px',
                                  borderRadius: '99px',
                                  background: 'var(--md-surface-container-highest)',
                                  color: 'var(--md-on-surface-variant)',
                                }}
                              >
                                {evInfo.emoji} {evInfo.name}
                              </span>
                            )}
                          </div>

                          <h3
                            style={{
                              fontSize: '16px',
                              fontWeight: 700,
                              marginTop: '2px',
                              wordBreak: 'break-word',
                            }}
                          >
                            {gift.title}
                          </h3>

                          {(gift.tracking || gift.notes || gift.url || gift.sourceWishlistShareId) && (
                            <div
                              style={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                alignItems: 'center',
                                gap: '10px',
                                marginTop: '5px',
                                fontSize: '12px',
                                color: 'var(--md-on-surface-variant)',
                              }}
                            >
                              {gift.sourceWishlistShareId && (
                                <a
                                  href={`/w/${gift.sourceWishlistShareId}${
                                    gift.sourceWishlistKey ? `?k=${gift.sourceWishlistKey}` : ''
                                  }`}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  style={{
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: '4px',
                                    fontSize: '11px',
                                    fontWeight: 700,
                                    padding: '2px 8px',
                                    borderRadius: '99px',
                                    background: gift.bought
                                      ? 'var(--md-success-container)'
                                      : 'var(--md-secondary-container)',
                                    color: gift.bought
                                      ? 'var(--md-on-success-container)'
                                      : 'var(--md-on-secondary-container)',
                                    textDecoration: 'none',
                                  }}
                                  title="Synced with live shared wishlist — toggling Bought syncs live"
                                >
                                  <Sparkles size={11} />
                                  {gift.bought ? 'Wishlist: Purchased ✓' : 'Wishlist: Researching 🔍'}
                                </a>
                              )}
                              {gift.url && (
                                <a
                                  href={gift.url}
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
                              {gift.tracking && (
                                <span
                                  style={{
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: '4px',
                                    fontFamily: 'var(--font-mono), monospace',
                                    fontSize: '11.5px',
                                  }}
                                >
                                  <Package size={12} /> {gift.tracking}
                                </span>
                              )}
                              {gift.notes && <span>{gift.notes}</span>}
                            </div>
                          )}
                        </div>
                      </div>

                      <div
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'flex-end',
                          gap: '6px',
                          flexShrink: 0,
                        }}
                      >
                        <span className="m3-display-num" style={{ fontSize: '18px' }}>
                          {gift.costLabel
                            ? gift.costLabel
                            : gift.cost > 0
                            ? `$${gift.cost.toLocaleString()}`
                            : '—'}
                        </span>

                        <div style={{ display: 'flex', gap: '4px' }}>
                          <button
                            type="button"
                            onClick={() => moveGiftToIdeas(gift.id, gift.bought)}
                            className="m3-btn-icon"
                            style={{ width: '32px', height: '32px' }}
                            title="Move back to Ideas / Save for Later"
                          >
                            <Bookmark size={14} />
                          </button>
                          <button
                            type="button"
                            onClick={() => onEditGift(gift)}
                            className="m3-btn-icon"
                            style={{ width: '32px', height: '32px' }}
                            title="Edit gift"
                          >
                            <Pencil size={14} />
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* M3 Expressive Connected Fulfillment Button Group */}
                    <div className="m3-connected-group" style={{ marginTop: '14px' }}>
                      <button
                        type="button"
                        className="m3-segment-btn"
                        data-kind="bought"
                        data-checked={gift.bought}
                        onClick={() => toggleGiftStatus(gift.id, 'bought')}
                      >
                        {gift.bought && <Check size={14} strokeWidth={2.8} />}
                        Bought
                      </button>
                      <button
                        type="button"
                        className="m3-segment-btn"
                        data-kind="received"
                        data-checked={gift.received}
                        onClick={() => toggleGiftStatus(gift.id, 'received')}
                      >
                        {gift.received && <Check size={14} strokeWidth={2.8} />}
                        Arrived
                      </button>
                      <button
                        type="button"
                        className="m3-segment-btn"
                        data-kind="wrapped"
                        data-checked={gift.wrapped}
                        onClick={() => toggleGiftStatus(gift.id, 'wrapped')}
                      >
                        {gift.wrapped && <Check size={14} strokeWidth={2.8} />}
                        Wrapped
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </div>

        {/* RIGHT / SUPPORTING PANE: Recipient Budget Standing + 1-Tap Idea Promotions */}
        <aside style={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
          <div className="m3-surface-card-low">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '12px',
              }}
            >
              <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Per-Person Spending</h3>
              <span style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
                Tap to filter
              </span>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {recipientBreakdown.map(({ person, spent, ideasCount }) => {
                const pct = person.budget > 0 ? Math.min(100, (spent / person.budget) * 100) : 100;
                const over = person.budget > 0 && spent > person.budget;
                const isSelected = personFilter === person.id;

                return (
                  <button
                    key={person.id}
                    type="button"
                    onClick={() => setPersonFilter(isSelected ? 'all' : person.id)}
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      padding: '10px 12px',
                      borderRadius: isSelected ? '20px' : '14px',
                      background: isSelected
                        ? 'var(--md-secondary-container)'
                        : 'var(--md-surface-container)',
                      color: isSelected
                        ? 'var(--md-on-secondary-container)'
                        : 'var(--md-on-surface)',
                      transition: 'all 240ms var(--spring-fast)',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        fontSize: '13px',
                        marginBottom: '5px',
                      }}
                    >
                      <span style={{ fontWeight: 700 }}>
                        {person.name}
                        {ideasCount > 0 && (
                          <span
                            style={{
                              fontWeight: 500,
                              fontSize: '11.5px',
                              opacity: 0.78,
                              marginLeft: '6px',
                            }}
                          >
                            • {ideasCount} idea{ideasCount > 1 ? 's' : ''}
                          </span>
                        )}
                      </span>
                      <span className="m3-display-num" style={{ fontSize: '13px' }}>
                        ${spent}{' '}
                        {person.budget > 0 && (
                          <span style={{ opacity: 0.7, fontWeight: 500 }}>/ ${person.budget}</span>
                        )}
                      </span>
                    </div>

                    <div
                      style={{
                        width: '100%',
                        height: '5px',
                        borderRadius: '99px',
                        background: 'var(--md-surface-container-lowest)',
                        overflow: 'hidden',
                      }}
                    >
                      <div
                        style={{
                          width: `${spent === 0 ? 0 : Math.max(8, pct)}%`,
                          height: '100%',
                          borderRadius: '99px',
                          background: over
                            ? 'var(--md-warning)'
                            : spent >= person.budget && person.budget > 0
                            ? 'var(--md-success)'
                            : 'var(--md-primary-vibrant)',
                          transition: 'width 320ms var(--spring-spatial)',
                        }}
                      />
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Quick Ideas Ready to Promote */}
          {suggestedIdeas.length > 0 && (
            <div className="m3-surface-card-low">
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: '10px',
                }}
              >
                <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Quick Pull from Ideas</h3>
                <button
                  type="button"
                  onClick={() => setActiveTab('ideas')}
                  style={{ fontSize: '12px', color: 'var(--md-primary)', fontWeight: 700 }}
                >
                  See all
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {suggestedIdeas.map(({ idea, person, targetEvId }) => (
                  <div
                    key={idea.id}
                    style={{
                      background: 'var(--md-surface-container)',
                      borderRadius: '14px',
                      padding: '10px 12px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '10px',
                    }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: '11.5px', fontWeight: 700, color: 'var(--md-primary)' }}>
                        {person?.name || 'Pool'} {idea.cost > 0 ? `• $${idea.cost}` : ''}
                      </div>
                      <div
                        style={{
                          fontSize: '13px',
                          fontWeight: 600,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {idea.title}
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => moveGiftToEvent(idea.id, targetEvId)}
                      className="m3-btn-tonal"
                      style={{ height: '32px', padding: '0 12px', fontSize: '12px', flexShrink: 0 }}
                    >
                      + Move
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
