'use client';

import React, { useEffect, useState } from 'react';
import { ArrowRight, CalendarPlus, X } from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import type { GiftItem, OccasionType } from '@/lib/types';

interface MoveToEventSheetProps {
  gift: GiftItem | null;
  onClose: () => void;
}

export function MoveToEventSheet({ gift, onClose }: MoveToEventSheetProps) {
  if (!gift) return null;
  return <MoveToEventForm key={gift.id} gift={gift} onClose={onClose} />;
}

function MoveToEventForm({ gift, onClose }: { gift: GiftItem; onClose: () => void }) {
  const { events, people, moveGiftToEvent, addEvent } = useGiftStore();
  const [personId, setPersonId] = useState<string>(() => gift.personId || '');
  const [selectedEventId, setSelectedEventId] = useState<string>(
    () => events.find((e) => !e.isArchived)?.id || events[0]?.id || '2026-christmas'
  );
  const [creatingNew, setCreatingNew] = useState(false);
  const [newYear, setNewYear] = useState(2026);
  const [newOccasion, setNewOccasion] = useState<OccasionType>('Christmas');
  const [newCustomName, setNewCustomName] = useState('');

  useEffect(() => {
    setNewYear(new Date().getFullYear());
  }, []);

  const handleConfirm = (e: React.FormEvent) => {
    e.preventDefault();
    let targetEventId = selectedEventId;
    if (creatingNew) {
      const emojiMap: Record<OccasionType, string> = {
        Christmas: '🎄',
        Birthday: '🎂',
        Anniversary: '💝',
        Other: '✨',
      };
      const name =
        newCustomName.trim() ||
        `${newYear} ${newOccasion === 'Birthday' ? 'Birthdays' : newOccasion}`;
      const created = addEvent({
        name,
        year: newYear,
        occasion: newOccasion,
        emoji: emojiMap[newOccasion],
        isActive: true,
        isArchived: false,
      });
      targetEventId = created.id;
    }

    moveGiftToEvent(gift.id, targetEventId, personId || null);
    onClose();
  };

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div className="m3-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="m3-drag-handle" />
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '14px',
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
              Assign Idea to Occasion
            </span>
            <h2 style={{ fontSize: '19px', fontWeight: 700 }}>{gift.title}</h2>
          </div>
          <button type="button" onClick={onClose} className="m3-btn-icon" aria-label="Close">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleConfirm} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <label className="m3-field-label" htmlFor="move-person-select">
              Who is this gift for?
            </label>
            <select
              id="move-person-select"
              value={personId}
              onChange={(e) => setPersonId(e.target.value)}
              className="m3-select"
            >
              <option value="">Decide later (General Pool)</option>
              {people.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} {p.budget > 0 ? `(Budget $${p.budget})` : ''}
                </option>
              ))}
            </select>
          </div>

          {!creatingNew ? (
            <div>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: '8px',
                }}
              >
                <span className="m3-field-label" style={{ marginBottom: 0 }}>
                  Choose Event / Year Page
                </span>
                <button
                  type="button"
                  onClick={() => setCreatingNew(true)}
                  style={{
                    fontSize: '12.5px',
                    fontWeight: 700,
                    color: 'var(--md-primary)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  <CalendarPlus size={14} /> New Occasion
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                {events.map((ev) => {
                  const active = selectedEventId === ev.id;
                  return (
                    <button
                      key={ev.id}
                      type="button"
                      onClick={() => setSelectedEventId(ev.id)}
                      style={{
                        padding: '12px 14px',
                        borderRadius: active ? '999px' : '16px',
                        background: active
                          ? 'var(--md-primary-container)'
                          : 'var(--md-surface-container)',
                        color: active
                          ? 'var(--md-on-primary-container)'
                          : 'var(--md-on-surface)',
                        textAlign: 'left',
                        fontWeight: 600,
                        fontSize: '13.5px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        transition: 'all 260ms var(--spring-fast)',
                      }}
                    >
                      <span style={{ fontSize: '16px' }}>{ev.emoji}</span>
                      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {ev.name}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ) : (
            <div
              style={{
                background: 'var(--md-surface-container)',
                padding: '14px',
                borderRadius: '20px',
                display: 'flex',
                flexDirection: 'column',
                gap: '10px',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '13px', fontWeight: 700 }}>Create New Occasion</span>
                <button
                  type="button"
                  onClick={() => setCreatingNew(false)}
                  style={{ fontSize: '12px', color: 'var(--md-primary)', fontWeight: 600 }}
                >
                  Pick existing instead
                </button>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr', gap: '10px' }}>
                <div>
                  <label className="m3-field-label">Year</label>
                  <input
                    type="number"
                    value={newYear}
                    onChange={(e) => setNewYear(Number(e.target.value) || 2026)}
                    className="m3-input"
                  />
                </div>
                <div>
                  <label className="m3-field-label">Occasion Type</label>
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
              </div>
              <div>
                <label className="m3-field-label">Custom Title (Optional)</label>
                <input
                  type="text"
                  value={newCustomName}
                  onChange={(e) => setNewCustomName(e.target.value)}
                  placeholder={`e.g., ${newYear} ${newOccasion}`}
                  className="m3-input"
                />
              </div>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '4px' }}>
            <button type="button" onClick={onClose} className="m3-btn-tonal">
              Cancel
            </button>
            <button type="submit" className="m3-btn-filled">
              Move Gift <ArrowRight size={16} />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
