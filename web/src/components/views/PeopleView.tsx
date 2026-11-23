'use client';

import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpDown,
  ChevronDown,
  ChevronUp,
  Clock,
  GripVertical,
  Pencil,
  Plus,
  Ruler,
  Search,
  StickyNote,
  UserPlus,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { ExpressiveProgress } from '@/components/ui/ExpressiveProgress';
import type { GiftItem, Person } from '@/lib/types';

export type PeopleSortOption =
  | 'default'
  | 'name-asc'
  | 'name-desc'
  | 'budget-desc'
  | 'spent-desc'
  | 'ideas-desc';

interface PeopleViewProps {
  onOpenPersonEditor: (person?: Person | null) => void;
  onOpenNewGiftForPerson: (personId: string) => void;
  onEditGift: (gift: GiftItem) => void;
}

export function PeopleView({
  onOpenPersonEditor,
  onOpenNewGiftForPerson,
  onEditGift,
}: PeopleViewProps) {
  const {
    people,
    events,
    gifts,
    selectedPersonId,
    setSelectedPersonId,
    updatePerson,
    moveGiftToEvent,
    reorderPeople,
  } = useGiftStore();

  const [searchQuery, setSearchQuery] = useState('');
  const [sortOption, setSortOption] = useState<PeopleSortOption>('default');
  const [mobileDetailOpen, setMobileDetailOpen] = useState(false);
  const [showOrderButtons, setShowOrderButtons] = useState(false);
  const [draggedPersonId, setDraggedPersonId] = useState<string | null>(null);
  const [dragOverPersonId, setDragOverPersonId] = useState<string | null>(null);
  const [dropPosition, setDropPosition] = useState<'before' | 'after'>('after');

  const eventMap = useMemo(() => new Map(events.map((e) => [e.id, e])), [events]);

  // Per-person quick stats
  const personStatsMap = useMemo(() => {
    const map = new Map<string, { xmasSpent: number; ideasCount: number }>();
    for (const p of people) {
      map.set(p.id, { xmasSpent: 0, ideasCount: 0 });
    }
    for (const g of gifts) {
      if (!g.personId) continue;
      const entry = map.get(g.personId) || { xmasSpent: 0, ideasCount: 0 };
      if (g.eventId === '2026-christmas') {
        entry.xmasSpent += g.cost || 0;
      } else if (g.eventId === null) {
        entry.ideasCount += 1;
      }
      map.set(g.personId, entry);
    }
    return map;
  }, [people, gifts]);

  const filteredPeople = useMemo(() => {
    let list = [...people];
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.notes.toLowerCase().includes(q) ||
          Object.values(p.sizes || {}).some((s) => s?.toLowerCase().includes(q))
      );
    }

    switch (sortOption) {
      case 'name-asc':
        return list.sort((a, b) => a.name.localeCompare(b.name));
      case 'name-desc':
        return list.sort((a, b) => b.name.localeCompare(a.name));
      case 'budget-desc':
        return list.sort((a, b) => (b.budget || 0) - (a.budget || 0));
      case 'spent-desc':
        return list.sort((a, b) => {
          const spentA = personStatsMap.get(a.id)?.xmasSpent || 0;
          const spentB = personStatsMap.get(b.id)?.xmasSpent || 0;
          return spentB - spentA;
        });
      case 'ideas-desc':
        return list.sort((a, b) => {
          const ideasA = personStatsMap.get(a.id)?.ideasCount || 0;
          const ideasB = personStatsMap.get(b.id)?.ideasCount || 0;
          return ideasB - ideasA;
        });
      case 'default':
      default:
        return list.sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
    }
  }, [people, searchQuery, sortOption, personStatsMap]);

  const activePerson = useMemo(
    () => people.find((p) => p.id === selectedPersonId) || people[0] || null,
    [people, selectedPersonId]
  );

  const handleDragStart = (e: React.DragEvent, id: string) => {
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', id);
    setDraggedPersonId(id);
  };

  const handleDragOver = (e: React.DragEvent, id: string) => {
    if (draggedPersonId === id) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    const rect = e.currentTarget.getBoundingClientRect();
    const midY = rect.top + rect.height / 2;
    const pos = e.clientY < midY ? 'before' : 'after';
    setDragOverPersonId(id);
    setDropPosition(pos);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
    setDragOverPersonId(null);
  };

  const handleDrop = (e: React.DragEvent, targetId: string) => {
    e.preventDefault();
    if (!draggedPersonId || draggedPersonId === targetId) {
      setDraggedPersonId(null);
      setDragOverPersonId(null);
      return;
    }

    const currentOrder = [...people].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
    const draggedIdx = currentOrder.findIndex((p) => p.id === draggedPersonId);
    const targetIdx = currentOrder.findIndex((p) => p.id === targetId);

    if (draggedIdx === -1 || targetIdx === -1) {
      setDraggedPersonId(null);
      setDragOverPersonId(null);
      return;
    }

    const itemToMove = currentOrder[draggedIdx];
    const without = currentOrder.filter((p) => p.id !== draggedPersonId);
    let insertIdx = without.findIndex((p) => p.id === targetId);
    if (dropPosition === 'after') {
      insertIdx += 1;
    }
    without.splice(insertIdx, 0, itemToMove);

    reorderPeople(without.map((p) => p.id));
    setDraggedPersonId(null);
    setDragOverPersonId(null);
  };

  const handleDragEnd = () => {
    setDraggedPersonId(null);
    setDragOverPersonId(null);
  };

  const handleMoveStep = (id: string, direction: 'up' | 'down') => {
    const currentOrder = [...people].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
    const idx = currentOrder.findIndex((p) => p.id === id);
    if (idx === -1) return;
    const targetIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= currentOrder.length) return;

    const copy = [...currentOrder];
    const [moved] = copy.splice(idx, 1);
    copy.splice(targetIdx, 0, moved);

    reorderPeople(copy.map((p) => p.id));
  };

  // Selected person's gifts categorized
  const activePersonData = useMemo(() => {
    if (!activePerson) return null;
    const personGifts = gifts.filter((g) => g.personId === activePerson.id);
    const ideas = personGifts.filter((g) => g.eventId === null);
    const active2026 = personGifts.filter((g) => {
      if (!g.eventId) return false;
      const ev = eventMap.get(g.eventId);
      return ev && !ev.isArchived;
    });
    const pastHistory = personGifts.filter((g) => {
      if (!g.eventId) return false;
      const ev = eventMap.get(g.eventId);
      return ev?.isArchived;
    });

    const xmasSpent = active2026
      .filter((g) => g.eventId === '2026-christmas')
      .reduce((acc, g) => acc + (g.cost || 0), 0);
    const ideasValue = ideas.reduce((acc, g) => acc + (g.cost || 0), 0);

    return {
      ideas,
      active2026,
      pastHistory,
      xmasSpent,
      ideasValue,
    };
  }, [activePerson, gifts, eventMap]);

  return (
    <div className="canonical-list-detail">
      {/* LEFT PANE: Searchable Recipient Directory */}
      <div
        className={mobileDetailOpen ? 'people-master-pane mobile-hidden' : 'people-master-pane'}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
          minWidth: 0,
        }}
      >
        <style>{`
          @media (max-width: 759px) {
            .people-master-pane.mobile-hidden { display: none !important; }
            .people-detail-pane.mobile-closed { display: none !important; }
          }
          @media (min-width: 760px) {
            .mobile-back-pill { display: none !important; }
          }
        `}</style>

        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <div style={{ position: 'relative', flex: 1 }}>
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
              placeholder="Search people, sizes, or notes..."
              className="m3-input"
              style={{ paddingLeft: '38px', borderRadius: '999px', height: '42px' }}
            />
          </div>

          <button
            type="button"
            onClick={() => onOpenPersonEditor(null)}
            className="m3-btn-filled"
            style={{ padding: '0 16px', flexShrink: 0 }}
          >
            <UserPlus size={16} /> Add
          </button>
        </div>

        {/* Sort & Reorder Bar */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '8px',
            padding: '2px 4px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flex: 1, minWidth: 0 }}>
            <ArrowUpDown size={14} style={{ color: 'var(--md-primary)', flexShrink: 0 }} />
            <select
              value={sortOption}
              onChange={(e) => setSortOption(e.target.value as PeopleSortOption)}
              className="m3-input"
              style={{
                height: '34px',
                fontSize: '12px',
                borderRadius: '12px',
                padding: '0 8px',
                flex: 1,
                cursor: 'pointer',
              }}
              aria-label="Sort recipients"
            >
              <option value="default">Default (Custom Order)</option>
              <option value="name-asc">Name (A → Z)</option>
              <option value="name-desc">Name (Z → A)</option>
              <option value="budget-desc">Budget (High → Low)</option>
              <option value="spent-desc">Spent (High → Low)</option>
              <option value="ideas-desc">Ideas Count (Most → Least)</option>
            </select>
          </div>

          {sortOption === 'default' && !searchQuery.trim() && people.length > 1 && (
            <button
              type="button"
              onClick={() => setShowOrderButtons((prev) => !prev)}
              className="m3-btn-tonal"
              style={{ height: '34px', fontSize: '11.5px', padding: '0 10px', flexShrink: 0 }}
              title="Toggle Up/Down move buttons for mobile or precise reordering"
            >
              {showOrderButtons ? 'Done' : 'Reorder'}
            </button>
          )}
        </div>

        {sortOption === 'default' && !searchQuery.trim() && people.length > 1 && !showOrderButtons && (
          <span
            style={{
              fontSize: '11px',
              color: 'var(--md-on-surface-variant)',
              paddingLeft: '6px',
              marginTop: '-4px',
            }}
          >
            💡 Drag cards to rearrange default order, or tap <strong>Reorder</strong>.
          </span>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {filteredPeople.map((person, index) => {
            const selected = activePerson?.id === person.id;
            const isDragging = draggedPersonId === person.id;
            const isOver = dragOverPersonId === person.id;
            const canDrag = sortOption === 'default' && !searchQuery.trim();

            const st = personStatsMap.get(person.id) || { xmasSpent: 0, ideasCount: 0 };
            const sizeSummary = [
              person.sizes?.top ? `Top ${person.sizes.top}` : null,
              person.sizes?.bottoms ? `Bot ${person.sizes.bottoms}` : null,
            ]
              .filter(Boolean)
              .join(' • ');

            let dropShadow = 'none';
            if (isOver && !isDragging) {
              dropShadow =
                dropPosition === 'before'
                  ? 'inset 0 3px 0 0 var(--md-primary)'
                  : 'inset 0 -3px 0 0 var(--md-primary)';
            }

            return (
              <div
                key={person.id}
                draggable={canDrag}
                onDragStart={(e) => canDrag && handleDragStart(e, person.id)}
                onDragOver={(e) => canDrag && handleDragOver(e, person.id)}
                onDragLeave={handleDragLeave}
                onDrop={(e) => canDrag && handleDrop(e, person.id)}
                onDragEnd={handleDragEnd}
                style={{
                  width: '100%',
                  padding: '12px 14px',
                  borderRadius: selected ? '24px' : '18px',
                  background: selected
                    ? 'var(--md-secondary-container)'
                    : 'var(--md-surface-container)',
                  color: selected
                    ? 'var(--md-on-secondary-container)'
                    : 'var(--md-on-surface)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '10px',
                  transition: 'all 200ms ease',
                  opacity: isDragging ? 0.35 : 1,
                  boxShadow: dropShadow,
                  cursor: canDrag ? 'grab' : 'pointer',
                  userSelect: 'none',
                }}
                onClick={() => {
                  setSelectedPersonId(person.id);
                  setMobileDetailOpen(true);
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0, flex: 1 }}>
                  {canDrag && (
                    <div
                      title="Drag to reorder"
                      style={{
                        cursor: 'grab',
                        display: 'flex',
                        alignItems: 'center',
                        color: 'var(--md-on-surface-variant)',
                        opacity: 0.6,
                        flexShrink: 0,
                      }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <GripVertical size={16} />
                    </div>
                  )}

                  <div
                    className="m3-avatar"
                    data-shape={person.avatarShape}
                    data-tone={person.avatarTone}
                    style={{ flexShrink: 0 }}
                  >
                    {person.name.slice(0, 2).toUpperCase()}
                  </div>

                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ fontWeight: 700, fontSize: '15px' }}>{person.name}</div>
                    <div
                      style={{
                        fontSize: '12px',
                        opacity: 0.78,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {sizeSummary || person.relationship}
                      {st.ideasCount > 0 ? ` • ${st.ideasCount} ideas` : ''}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                  {showOrderButtons && canDrag && (
                    <div
                      style={{ display: 'flex', gap: '2px', alignItems: 'center' }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        type="button"
                        disabled={index === 0}
                        onClick={() => handleMoveStep(person.id, 'up')}
                        className="m3-btn-icon"
                        style={{
                          width: '28px',
                          height: '28px',
                          opacity: index === 0 ? 0.3 : 0.8,
                        }}
                        aria-label="Move Up"
                        title="Move Up"
                      >
                        <ChevronUp size={14} />
                      </button>
                      <button
                        type="button"
                        disabled={index === filteredPeople.length - 1}
                        onClick={() => handleMoveStep(person.id, 'down')}
                        className="m3-btn-icon"
                        style={{
                          width: '28px',
                          height: '28px',
                          opacity: index === filteredPeople.length - 1 ? 0.3 : 0.8,
                        }}
                        aria-label="Move Down"
                        title="Move Down"
                      >
                        <ChevronDown size={14} />
                      </button>
                    </div>
                  )}

                  <div style={{ textAlign: 'right' }}>
                    {person.budget > 0 ? (
                      <>
                        <div className="m3-display-num" style={{ fontSize: '14px' }}>
                          ${st.xmasSpent} / ${person.budget}
                        </div>
                        <span style={{ fontSize: '11px', opacity: 0.75 }}>2026 Xmas</span>
                      </>
                    ) : st.xmasSpent > 0 ? (
                      <div className="m3-display-num" style={{ fontSize: '14px' }}>
                        ${st.xmasSpent}
                      </div>
                    ) : (
                      <span style={{ fontSize: '11.5px', opacity: 0.7 }}>Profile →</span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* RIGHT PANE: Selected Recipient Profile, Sizes Cheat Sheet, Notes & History */}
      {activePerson && activePersonData && (
        <div
          className={!mobileDetailOpen ? 'people-detail-pane mobile-closed' : 'people-detail-pane'}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
            minWidth: 0,
          }}
        >
          {/* Mobile Back Pill */}
          <button
            type="button"
            onClick={() => setMobileDetailOpen(false)}
            className="m3-btn-tonal mobile-back-pill"
            style={{
              alignSelf: 'flex-start',
            }}
          >
            <ArrowLeft size={15} /> All People
          </button>

          {/* Expressive Recipient Hero Card */}
          <div className="m3-hero-card">
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '14px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <div
                  className="m3-avatar"
                  data-shape={activePerson.avatarShape}
                  data-tone={activePerson.avatarTone}
                  style={{ width: '58px', height: '58px', fontSize: '20px' }}
                >
                  {activePerson.name.slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <span style={{ fontSize: '12px', color: 'var(--md-primary)', fontWeight: 700 }}>
                    {activePerson.relationship}
                    {activePerson.birthday ? ` • 🎂 ${activePerson.birthday}` : ''}
                  </span>
                  <h2 style={{ fontSize: '24px', fontWeight: 800, lineHeight: 1.15 }}>
                    {activePerson.name}
                  </h2>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  onClick={() => onOpenPersonEditor(activePerson)}
                  className="m3-btn-tonal"
                >
                  <Pencil size={14} /> Edit Sizes & Profile
                </button>
                <button
                  type="button"
                  onClick={() => onOpenNewGiftForPerson(activePerson.id)}
                  className="m3-btn-filled"
                >
                  <Plus size={15} /> Add Gift
                </button>
              </div>
            </div>

            {activePerson.budget > 0 && (
              <div style={{ marginTop: '16px' }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: '13px',
                    marginBottom: '6px',
                  }}
                >
                  <span style={{ color: 'var(--md-on-surface-variant)', fontWeight: 600 }}>
                    2026 Christmas Budget
                  </span>
                  <span className="m3-display-num">
                    ${activePersonData.xmasSpent} spent of ${activePerson.budget} target
                  </span>
                </div>
                <ExpressiveProgress
                  value={(activePersonData.xmasSpent / activePerson.budget) * 100}
                  overBudget={activePersonData.xmasSpent > activePerson.budget}
                />
              </div>
            )}
          </div>

          {/* Clothing Sizes Cheat Sheet + Brainstorming Notes Row */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '14px',
            }}
          >
            {/* Sizes Cheat Sheet */}
            <section className="m3-surface-card">
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: '12px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700 }}>
                  <Ruler size={16} style={{ color: 'var(--md-primary)' }} />
                  <span>Sizes Cheat Sheet</span>
                </div>
                <button
                  type="button"
                  onClick={() => onOpenPersonEditor(activePerson)}
                  style={{ fontSize: '12px', color: 'var(--md-primary)', fontWeight: 700 }}
                >
                  Edit
                </button>
              </div>

              {Object.keys(activePerson.sizes || {}).length === 0 ? (
                <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)' }}>
                  No sizes saved yet. Tap <strong>Edit</strong> to record shirt, pants, or shoe sizes.
                </p>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  {activePerson.sizes.top && (
                    <SizeTile label="T-Shirt / Top" value={activePerson.sizes.top} />
                  )}
                  {activePerson.sizes.bottoms && (
                    <SizeTile label="Bottoms / Pants" value={activePerson.sizes.bottoms} />
                  )}
                  {activePerson.sizes.intimates && (
                    <SizeTile label="Bra / Intimates" value={activePerson.sizes.intimates} />
                  )}
                  {activePerson.sizes.shoe && (
                    <SizeTile label="Shoe / Other" value={activePerson.sizes.shoe} />
                  )}
                </div>
              )}
            </section>

            {/* Brainstorming Notes */}
            <section className="m3-surface-card">
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  fontWeight: 700,
                  marginBottom: '10px',
                }}
              >
                <StickyNote size={16} style={{ color: 'var(--md-tertiary)' }} />
                <span>Interests & Notes</span>
              </div>
              <textarea
                rows={3}
                value={activePerson.notes}
                onChange={(e) => updatePerson(activePerson.id, { notes: e.target.value })}
                placeholder={`Jot down ${activePerson.name}'s favorite hobbies, teams, brands, or gift clues...`}
                className="m3-textarea"
                style={{ fontSize: '13.5px' }}
              />
            </section>
          </div>

          {/* Gift Ideas Backlog for this Person */}
          <section className="m3-surface-card">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '12px',
              }}
            >
              <h3 style={{ fontSize: '15.5px', fontWeight: 700 }}>
                💡 Gift Ideas ({activePersonData.ideas.length})
              </h3>
              {activePersonData.ideasValue > 0 && (
                <span style={{ fontSize: '12.5px', color: 'var(--md-on-surface-variant)' }}>
                  ${activePersonData.ideasValue.toLocaleString()} total ideas value
                </span>
              )}
            </div>

            {activePersonData.ideas.length === 0 ? (
              <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)' }}>
                No unassigned ideas right now.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {activePersonData.ideas.map((idea) => (
                  <div
                    key={idea.id}
                    style={{
                      background: 'var(--md-surface-container-low)',
                      borderRadius: '16px',
                      padding: '10px 14px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '10px',
                    }}
                  >
                    <div onClick={() => onEditGift(idea)} style={{ cursor: 'pointer', minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: '14px' }}>{idea.title}</div>
                      <div style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
                        {idea.cost > 0 ? `$${idea.cost}` : 'No cost set'}
                        {idea.store ? ` • ${idea.store}` : ''}
                        {idea.saveForLater ? ' • 📦 In Stockpile' : ''}
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => moveGiftToEvent(idea.id, '2026-christmas')}
                      className="m3-btn-tonal"
                      style={{ height: '34px', fontSize: '12px', flexShrink: 0 }}
                    >
                      🎄 2026 Xmas <ArrowRight size={13} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Active 2026 Occasions + Past Anti-Duplicate History */}
          <section className="m3-surface-card">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                marginBottom: '12px',
              }}
            >
              <Clock size={16} style={{ color: 'var(--md-secondary)' }} />
              <h3 style={{ fontSize: '15.5px', fontWeight: 700 }}>
                Current & Past Gift History (Anti-Duplicate Log)
              </h3>
            </div>

            {activePersonData.active2026.length === 0 &&
            activePersonData.pastHistory.length === 0 ? (
              <p style={{ fontSize: '13px', color: 'var(--md-on-surface-variant)' }}>
                No gifts recorded in events yet.
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {[...activePersonData.active2026, ...activePersonData.pastHistory].map((g) => {
                  const ev = g.eventId ? eventMap.get(g.eventId) : undefined;
                  return (
                    <div
                      key={g.id}
                      onClick={() => onEditGift(g)}
                      style={{
                        background: 'var(--md-surface-container-low)',
                        borderRadius: '14px',
                        padding: '10px 14px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '10px',
                        cursor: 'pointer',
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: 600, fontSize: '14px' }}>{g.title}</div>
                        <div style={{ fontSize: '12px', color: 'var(--md-on-surface-variant)' }}>
                          {ev ? `${ev.emoji} ${ev.name}` : 'Event'}
                          {g.store ? ` • ${g.store}` : ''}
                        </div>
                      </div>
                      <span className="m3-display-num" style={{ fontSize: '14px' }}>
                        {g.cost > 0 ? `$${g.cost}` : ''}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

function SizeTile({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        background: 'var(--md-surface-container-low)',
        padding: '10px 12px',
        borderRadius: '14px',
      }}
    >
      <span style={{ fontSize: '11px', color: 'var(--md-on-surface-variant)', display: 'block' }}>
        {label}
      </span>
      <strong style={{ fontSize: '15px' }}>{value}</strong>
    </div>
  );
}
