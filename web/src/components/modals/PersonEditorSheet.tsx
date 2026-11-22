'use client';

import React, { useState } from 'react';
import { Trash2, X } from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import type { AvatarShape, AvatarTone, Person } from '@/lib/types';

interface PersonEditorSheetProps {
  open: boolean;
  onClose: () => void;
  editingPerson?: Person | null;
}

const SHAPES: Array<{ id: AvatarShape; label: string }> = [
  { id: 'squircle', label: 'Squircle' },
  { id: 'cookie', label: 'Scallop' },
  { id: 'clover', label: 'Leaf' },
  { id: 'arch', label: 'Arch' },
  { id: 'flower', label: 'Petal' },
  { id: 'pill', label: 'Circle' },
];

const TONES: AvatarTone[] = ['terracotta', 'sage', 'amber', 'teal', 'rose', 'ochre'];

export function PersonEditorSheet({
  open,
  onClose,
  editingPerson,
}: PersonEditorSheetProps) {
  if (!open) return null;
  return (
    <PersonEditorForm
      key={editingPerson ? `edit-${editingPerson.id}` : 'new-person'}
      onClose={onClose}
      editingPerson={editingPerson}
    />
  );
}

function PersonEditorForm({
  onClose,
  editingPerson,
}: Omit<PersonEditorSheetProps, 'open'>) {
  const { addPerson, updatePerson, deletePerson } = useGiftStore();

  const [name, setName] = useState(() => (editingPerson ? editingPerson.name : ''));
  const [budget, setBudget] = useState(() =>
    editingPerson ? (editingPerson.budget > 0 ? String(editingPerson.budget) : '0') : '50'
  );
  const [relationship, setRelationship] = useState(() =>
    editingPerson ? editingPerson.relationship || 'Family & Close Circle' : 'Family & Close Circle'
  );
  const [birthday, setBirthday] = useState(() => (editingPerson ? editingPerson.birthday || '' : ''));
  const [topSize, setTopSize] = useState(() => (editingPerson ? editingPerson.sizes?.top || '' : ''));
  const [bottomSize, setBottomSize] = useState(() =>
    editingPerson ? editingPerson.sizes?.bottoms || '' : ''
  );
  const [intimatesSize, setIntimatesSize] = useState(() =>
    editingPerson ? editingPerson.sizes?.intimates || '' : ''
  );
  const [shoeSize, setShoeSize] = useState(() =>
    editingPerson ? editingPerson.sizes?.shoe || editingPerson.sizes?.other || '' : ''
  );
  const [notes, setNotes] = useState(() => (editingPerson ? editingPerson.notes || '' : ''));
  const [avatarShape, setAvatarShape] = useState<AvatarShape>(() =>
    editingPerson ? editingPerson.avatarShape || 'squircle' : 'cookie'
  );
  const [avatarTone, setAvatarTone] = useState<AvatarTone>(() =>
    editingPerson ? editingPerson.avatarTone || 'terracotta' : 'terracotta'
  );

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    const parsedBudget = Math.max(0, parseFloat(budget) || 0);

    const sizes = {
      ...(topSize.trim() ? { top: topSize.trim() } : {}),
      ...(bottomSize.trim() ? { bottoms: bottomSize.trim() } : {}),
      ...(intimatesSize.trim() ? { intimates: intimatesSize.trim() } : {}),
      ...(shoeSize.trim() ? { shoe: shoeSize.trim() } : {}),
    };

    if (editingPerson) {
      updatePerson(editingPerson.id, {
        name: name.trim(),
        budget: parsedBudget,
        relationship,
        birthday: birthday.trim() || undefined,
        sizes,
        notes: notes.trim(),
        avatarShape,
        avatarTone,
      });
    } else {
      addPerson({
        name: name.trim(),
        budget: parsedBudget,
        relationship,
        birthday: birthday.trim() || undefined,
        sizes,
        notes: notes.trim(),
        avatarShape,
        avatarTone,
      });
    }
    onClose();
  };

  const initials = (name.trim() || '?').slice(0, 2).toUpperCase();

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true">
      <div className="m3-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="m3-drag-handle" />
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '16px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div className="m3-avatar" data-shape={avatarShape} data-tone={avatarTone}>
              {initials}
            </div>
            <h2 style={{ fontSize: '19px', fontWeight: 700 }}>
              {editingPerson ? `Edit ${editingPerson.name}` : 'Add Person'}
            </h2>
          </div>
          <button type="button" onClick={onClose} className="m3-btn-icon" aria-label="Close">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1.3fr 0.9fr', gap: '12px' }}>
            <div>
              <label className="m3-field-label" htmlFor="person-name-input">
                Name *
              </label>
              <input
                id="person-name-input"
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g., Mom, Alex, Sam..."
                className="m3-input"
              />
            </div>

            <div>
              <label className="m3-field-label" htmlFor="person-budget-input">
                Target Budget ($)
              </label>
              <input
                id="person-budget-input"
                type="number"
                min="0"
                step="5"
                value={budget}
                onChange={(e) => setBudget(e.target.value)}
                className="m3-input"
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label className="m3-field-label">Circle / Group</label>
              <select
                value={relationship}
                onChange={(e) => setRelationship(e.target.value)}
                className="m3-select"
              >
                <option value="Family & Close Circle">Family & Close Circle</option>
                <option value="Friends & Extended">Friends & Extended</option>
                <option value="Extended & Brainstorming">Extended & Brainstorming</option>
              </select>
            </div>
            <div>
              <label className="m3-field-label">Birthday (Optional)</label>
              <input
                type="text"
                value={birthday}
                onChange={(e) => setBirthday(e.target.value)}
                placeholder="e.g., May 14"
                className="m3-input"
              />
            </div>
          </div>

          <div
            style={{
              background: 'var(--md-surface-container)',
              padding: '12px',
              borderRadius: '18px',
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
            }}
          >
            <span className="m3-field-label" style={{ marginBottom: 0 }}>
              Expressive Badge Shape & Color
            </span>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
              {SHAPES.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => setAvatarShape(s.id)}
                  className="m3-chip"
                  data-selected={avatarShape === s.id}
                  style={{ height: '32px', padding: '0 11px', fontSize: '12px' }}
                >
                  {s.label}
                </button>
              ))}
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              {TONES.map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setAvatarTone(t)}
                  className="m3-avatar"
                  data-shape="pill"
                  data-tone={t}
                  style={{
                    width: '30px',
                    height: '30px',
                    fontSize: '11px',
                    outline: avatarTone === t ? '2.5px solid var(--md-on-surface)' : 'none',
                    outlineOffset: '2px',
                  }}
                  aria-label={`Select ${t} tone`}
                >
                  {avatarTone === t ? '✓' : ''}
                </button>
              ))}
            </div>
          </div>

          <div
            style={{
              background: 'var(--md-surface-container)',
              padding: '14px',
              borderRadius: '18px',
            }}
          >
            <span className="m3-field-label">Sizes Cheat Sheet</span>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div>
                <label className="m3-field-label">T-Shirt / Top</label>
                <input
                  type="text"
                  value={topSize}
                  onChange={(e) => setTopSize(e.target.value)}
                  placeholder="M, L or XL..."
                  className="m3-input"
                />
              </div>
              <div>
                <label className="m3-field-label">Bottoms / Pants</label>
                <input
                  type="text"
                  value={bottomSize}
                  onChange={(e) => setBottomSize(e.target.value)}
                  placeholder="M, 32x30..."
                  className="m3-input"
                />
              </div>
              <div>
                <label className="m3-field-label">Bra / Intimates</label>
                <input
                  type="text"
                  value={intimatesSize}
                  onChange={(e) => setIntimatesSize(e.target.value)}
                  placeholder="Optional"
                  className="m3-input"
                />
              </div>
              <div>
                <label className="m3-field-label">Shoe / Ring / Other</label>
                <input
                  type="text"
                  value={shoeSize}
                  onChange={(e) => setShoeSize(e.target.value)}
                  placeholder="e.g., 9.5"
                  className="m3-input"
                />
              </div>
            </div>
          </div>

          <div>
            <label className="m3-field-label" htmlFor="person-notes-area">
              Interests, Hobbies & Brainstorming Notes
            </label>
            <textarea
              id="person-notes-area"
              rows={3}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Board games, Critical Role, coffee, favorite colors, dog's name..."
              className="m3-textarea"
            />
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '10px',
              marginTop: '4px',
            }}
          >
            {editingPerson ? (
              <button
                type="button"
                onClick={() => {
                  deletePerson(editingPerson.id);
                  onClose();
                }}
                className="m3-btn-tonal"
                style={{
                  background: 'var(--md-danger-container)',
                  color: 'var(--md-on-danger-container)',
                }}
              >
                <Trash2 size={15} /> Remove Person
              </button>
            ) : (
              <span />
            )}

            <div style={{ display: 'flex', gap: '8px', marginLeft: 'auto' }}>
              <button type="button" onClick={onClose} className="m3-btn-tonal">
                Cancel
              </button>
              <button type="submit" className="m3-btn-filled">
                {editingPerson ? 'Save Profile' : 'Add Person'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
