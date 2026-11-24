'use client';

import React, { useMemo, useState } from 'react';
import {
  ArrowRight,
  ChevronDown,
  ExternalLink,
  PackageCheck,
  Pencil,
  Plus,
  Search,
  Sparkles,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import type { GiftItem } from '@/lib/types';

interface IdeasViewProps {
  onOpenNewIdea: (defaults?: { personId?: string | null; eventId?: string | null }) => void;
  onEditGift: (gift: GiftItem) => void;
  onOpenMoveModal: (gift: GiftItem) => void;
}

export function IdeasView({
  onOpenNewIdea,
  onEditGift,
  onOpenMoveModal,
}: IdeasViewProps) {
  const { people, events, gifts, moveGiftToEvent } = useGiftStore();

  const [modeFilter, setModeFilter] = useState<'all' | 'ideas' | 'stockpile' | 'pool'>('all');
  const [personFilter, setPersonFilter] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const personMap = useMemo(() => new Map(people.map((p) => [p.id, p])), [people]);
  const primaryActiveEvent = useMemo(
    () => events.find((e) => e.id === '2026-christmas') || events.find((e) => !e.isArchived) || events[0],
    [events]
  );

  const backlogItems = useMemo(
    () => gifts.filter((g) => g.eventId === null),
    [gifts]
  );

  const counts = useMemo(() => {
    return {
      all: backlogItems.length,
      ideas: backlogItems.filter((g) => !g.saveForLater).length,
      stockpile: backlogItems.filter((g) => g.saveForLater).length,
      pool: backlogItems.filter((g) => !g.personId).length,
    };
  }, [backlogItems]);

  const filteredIdeas = useMemo(() => {
    return backlogItems.filter((g) => {
      if (modeFilter === 'ideas' && g.saveForLater) return false;
      if (modeFilter === 'stockpile' && !g.saveForLater) return false;
      if (modeFilter === 'pool' && g.personId !== null) return false;
      if (personFilter !== 'all' && g.personId !== personFilter) return false;
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
  }, [backlogItems, modeFilter, personFilter, searchQuery, personMap]);

  const peopleWithIdeas = useMemo(() => {
    const countsByPerson = new Map<string, number>();
    for (const g of backlogItems) {
      if (g.personId) {
        countsByPerson.set(g.personId, (countsByPerson.get(g.personId) || 0) + 1);
      }
    }
    return people
      .filter((p) => (countsByPerson.get(p.id) || 0) > 0)
      .map((p) => ({ person: p, count: countsByPerson.get(p.id) || 0 }));
  }, [backlogItems, people]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
      {/* Top Banner & Quick Action */}
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
            Year-Round Brainstorming & Stockpile
          </span>
          <h2 style={{ fontSize: '22px', fontWeight: 700, marginTop: '2px' }}>
            {counts.all} Gift Ideas Ready
          </h2>
          <p style={{ fontSize: '13.5px', color: 'var(--md-on-surface-variant)', marginTop: '2px' }}>
            Use the split button on any card to move straight to{' '}
            <strong>{primaryActiveEvent?.name || '2026 Christmas'}</strong> or pick another occasion.
          </p>
        </div>

        <button
          type="button"
          onClick={() =>
            onOpenNewIdea({
              personId: personFilter !== 'all' ? personFilter : null,
              eventId: null,
            })
          }
          className="m3-btn-filled"
        >
          <Plus size={16} /> New Gift Idea
        </button>
      </div>

      {/* Category & Person Filter Chips */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
          <div style={{ position: 'relative', flex: '1 1 220px' }}>
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
              placeholder="Search ideas, stores, or recipients..."
              className="m3-input"
              style={{ paddingLeft: '38px', borderRadius: '999px', height: '40px' }}
            />
          </div>

          <div className="m3-chip-row">
            <button
              type="button"
              className="m3-chip"
              data-selected={modeFilter === 'all'}
              onClick={() => setModeFilter('all')}
            >
              All ({counts.all})
            </button>
            <button
              type="button"
              className="m3-chip"
              data-selected={modeFilter === 'ideas'}
              onClick={() => setModeFilter('ideas')}
            >
              💡 Unbought Ideas ({counts.ideas})
            </button>
            <button
              type="button"
              className="m3-chip"
              data-selected={modeFilter === 'stockpile'}
              onClick={() => setModeFilter('stockpile')}
            >
              📦 Bought & Saved ({counts.stockpile})
            </button>
            <button
              type="button"
              className="m3-chip"
              data-selected={modeFilter === 'pool'}
              onClick={() => {
                setModeFilter('pool');
                setPersonFilter('all');
              }}
            >
              🎯 Not Sure Who ({counts.pool})
            </button>
          </div>
        </div>

        {/* Recipient Filter Row */}
        <div className="m3-chip-row">
          <button
            type="button"
            className="m3-chip"
            data-selected={personFilter === 'all'}
            onClick={() => setPersonFilter('all')}
            style={{ height: '34px', fontSize: '12px' }}
          >
            Everyone
          </button>
          {peopleWithIdeas.map(({ person, count }) => (
            <button
              key={person.id}
              type="button"
              className="m3-chip"
              data-selected={personFilter === person.id}
              onClick={() => {
                setPersonFilter(personFilter === person.id ? 'all' : person.id);
                if (modeFilter === 'pool') setModeFilter('all');
              }}
              style={{ height: '34px', fontSize: '12px' }}
            >
              {person.name} ({count})
            </button>
          ))}
        </div>
      </div>

      {/* Responsive Ideas Grid */}
      {filteredIdeas.length === 0 ? (
        <div className="m3-surface-card-low" style={{ textAlign: 'center', padding: '40px 20px' }}>
          <Sparkles size={26} style={{ color: 'var(--md-primary)', margin: '0 auto 10px' }} />
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>No gift ideas match your filter</h3>
          <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)', marginTop: '4px' }}>
            Jot down an idea or share a product link straight from Chrome or Amazon on Android!
          </p>
        </div>
      ) : (
        <div className="ideas-grid">
          {filteredIdeas.map((idea) => {
            const person = idea.personId ? personMap.get(idea.personId) : undefined;
            return (
              <article
                key={idea.id}
                className="m3-surface-card"
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '14px',
                }}
              >
                <div>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      justifyContent: 'space-between',
                      gap: '10px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
                      <div
                        className="m3-avatar"
                        data-shape={person?.avatarShape || 'pill'}
                        data-tone={person?.avatarTone || 'ochre'}
                        style={{ width: '38px', height: '38px', fontSize: '13px' }}
                      >
                        {(person?.name || '?').slice(0, 2).toUpperCase()}
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--md-primary)' }}>
                          {person?.name || 'Not sure who (Pool)'}
                        </div>
                        {idea.store && (
                          <div style={{ fontSize: '11.5px', color: 'var(--md-on-surface-variant)' }}>
                            {idea.store}
                          </div>
                        )}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span className="m3-display-num" style={{ fontSize: '17px' }}>
                        {idea.costLabel
                          ? idea.costLabel
                          : idea.cost > 0
                          ? `$${idea.cost.toLocaleString()}`
                          : ''}
                      </span>
                      <button
                        type="button"
                        onClick={() => onEditGift(idea)}
                        className="m3-btn-icon"
                        style={{ width: '32px', height: '32px' }}
                        aria-label="Edit idea"
                      >
                        <Pencil size={14} />
                      </button>
                    </div>
                  </div>

                  <h3 style={{ fontSize: '15.5px', fontWeight: 700, marginTop: '10px' }}>
                    {idea.title}
                  </h3>

                  <div
                    style={{
                      display: 'flex',
                      flexWrap: 'wrap',
                      gap: '8px',
                      marginTop: '6px',
                      alignItems: 'center',
                    }}
                  >
                    {idea.sourceWishlistShareId && (
                      <a
                        href={`/w/${idea.sourceWishlistShareId}${
                          idea.sourceWishlistKey ? `?k=${idea.sourceWishlistKey}` : ''
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
                          background: idea.bought
                            ? 'var(--md-success-container)'
                            : 'var(--md-secondary-container)',
                          color: idea.bought
                            ? 'var(--md-on-success-container)'
                            : 'var(--md-on-secondary-container)',
                          textDecoration: 'none',
                        }}
                        title="Synced with live shared wishlist"
                      >
                        <Sparkles size={11} />
                        {idea.bought ? 'Wishlist: Purchased ✓' : 'Wishlist: Researching 🔍'}
                      </a>
                    )}

                    {idea.saveForLater && (
                      <span
                        style={{
                          fontSize: '11px',
                          fontWeight: 700,
                          padding: '2px 9px',
                          borderRadius: '99px',
                          background: 'var(--md-success-container)',
                          color: 'var(--md-on-success-container)',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <PackageCheck size={12} /> In Stockpile (Bought)
                      </span>
                    )}

                    {idea.url && (
                      <a
                        href={idea.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{
                          fontSize: '12px',
                          fontWeight: 600,
                          color: 'var(--md-primary)',
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

                {/* M3 Expressive Split Button: 1-Click Move to Primary Event + Chevron Occasion Picker */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '8px',
                    paddingTop: '10px',
                    borderTop: '1px solid var(--md-outline-variant)',
                  }}
                >
                  <div className="m3-split-btn" style={{ marginLeft: 'auto' }}>
                    <button
                      type="button"
                      className="m3-split-main"
                      onClick={() => {
                        if (!idea.personId) {
                          // Prompt recipient & occasion together if currently "Not sure who"
                          onOpenMoveModal(idea);
                        } else if (primaryActiveEvent) {
                          moveGiftToEvent(idea.id, primaryActiveEvent.id);
                        }
                      }}
                    >
                      <span>
                        {idea.personId
                          ? `${primaryActiveEvent?.emoji || '🎄'} ${primaryActiveEvent?.name || '2026 Christmas'}`
                          : 'Assign & Move'}
                      </span>
                      <ArrowRight size={14} />
                    </button>
                    <button
                      type="button"
                      className="m3-split-menu"
                      onClick={() => onOpenMoveModal(idea)}
                      aria-label="Choose another occasion or person"
                      title="Pick Birthday, Christmas, or new event"
                    >
                      <ChevronDown size={15} />
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
