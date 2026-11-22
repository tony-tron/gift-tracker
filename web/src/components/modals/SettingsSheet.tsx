'use client';

import React, { useState } from 'react';
import {
  Check,
  Cloud,
  CloudUpload,
  Database,
  Download,
  LogIn,
  LogOut,
  Moon,
  Palette,
  Settings,
  Sun,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import { parseFirebaseSnippet } from '@/lib/firebase';
import type { ColorMode, ThemeSeed } from '@/lib/types';

interface SettingsSheetProps {
  open: boolean;
  onClose: () => void;
}

const SEEDS: Array<{ id: ThemeSeed; name: string; swatch: string }> = [
  { id: 'terracotta', name: 'Festive Terracotta', swatch: '#f47c5d' },
  { id: 'forest', name: 'Botanical Sage', swatch: '#5eba73' },
  { id: 'ochre', name: 'Sunlit Ochre', swatch: '#eba328' },
  { id: 'ocean', name: 'Pacific Teal', swatch: '#3ab6cc' },
];

export function SettingsSheet({ open, onClose }: SettingsSheetProps) {
  const {
    themeSeed,
    setThemeSeed,
    colorMode,
    setColorMode,
    syncStatus,
    syncError,
    firebaseUser,
    firebaseConfig,
    connectFirebaseConfig,
    loginGoogle,
    loginAnonymousSync,
    logoutFirebase,
    pushLocalToCloud,
    resetData,
    exportJsonBackup,
    importJsonBackup,
  } = useGiftStore();

  const [snippetInput, setSnippetInput] = useState('');
  const [configError, setConfigError] = useState<string | null>(null);
  const [showConfigBox, setShowConfigBox] = useState(false);

  if (!open) return null;

  const handleSaveSnippet = async () => {
    const parsed = parseFirebaseSnippet(snippetInput);
    if (!parsed) {
      setConfigError(
        'Could not find apiKey and projectId. Paste the firebaseConfig object or JSON from Firebase Console.'
      );
      return;
    }
    setConfigError(null);
    await connectFirebaseConfig(parsed);
    setSnippetInput('');
    setShowConfigBox(false);
  };

  const handleDownloadBackup = () => {
    const json = exportJsonBackup();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `gift-tracker-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleUploadBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        importJsonBackup(reader.result);
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="m3-scrim" onClick={onClose} role="dialog" aria-modal="true" aria-label="Settings">
      <div className="m3-sheet" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '560px' }}>
        <div className="m3-drag-handle" />
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '18px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Settings size={20} style={{ color: 'var(--md-primary)' }} />
            <h2 style={{ fontSize: '20px', fontWeight: 700 }}>Settings</h2>
          </div>
          <button type="button" onClick={onClose} className="m3-btn-icon" aria-label="Close settings">
            <X size={18} />
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* 1. Appearance & Theme */}
          <section
            style={{
              background: 'var(--md-surface-container)',
              padding: '16px',
              borderRadius: '22px',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700 }}>
              <Palette size={17} style={{ color: 'var(--md-primary)' }} />
              <span>Appearance</span>
            </div>

            {/* Light / Dark / System Connected Toggle */}
            <div className="m3-connected-group">
              {(
                [
                  { id: 'dark', label: 'Dark', icon: <Moon size={14} /> },
                  { id: 'light', label: 'Light', icon: <Sun size={14} /> },
                  { id: 'system', label: 'System', icon: <SparkleDot /> },
                ] as Array<{ id: ColorMode; label: string; icon: React.ReactNode }>
              ).map((m) => (
                <button
                  key={m.id}
                  type="button"
                  className="m3-segment-btn"
                  data-kind="received"
                  data-checked={colorMode === m.id}
                  onClick={() => setColorMode(m.id)}
                >
                  {m.icon} {m.label}
                </button>
              ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px' }}>
              {SEEDS.map((s) => {
                const active = themeSeed === s.id;
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => setThemeSeed(s.id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      padding: '10px 12px',
                      borderRadius: active ? '999px' : '14px',
                      background: active
                        ? 'var(--md-primary-container)'
                        : 'var(--md-surface-container-low)',
                      color: active
                        ? 'var(--md-on-primary-container)'
                        : 'var(--md-on-surface)',
                      fontWeight: 600,
                      fontSize: '13px',
                      transition: 'all 260ms var(--spring-fast)',
                    }}
                  >
                    <span
                      style={{
                        width: '18px',
                        height: '18px',
                        borderRadius: '99px',
                        background: s.swatch,
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#111',
                        flexShrink: 0,
                      }}
                    >
                      {active && <Check size={12} strokeWidth={3} />}
                    </span>
                    <span>{s.name}</span>
                  </button>
                );
              })}
            </div>
          </section>

          {/* 2. Firebase Cloud Sync */}
          <section
            style={{
              background: 'var(--md-surface-container)',
              padding: '16px',
              borderRadius: '22px',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700 }}>
                <Cloud size={17} style={{ color: 'var(--md-primary)' }} />
                <span>Cloud Sync</span>
              </div>
              <span
                style={{
                  fontSize: '11.5px',
                  fontWeight: 700,
                  padding: '3px 10px',
                  borderRadius: '99px',
                  background:
                    syncStatus === 'synced'
                      ? 'var(--md-success-container)'
                      : 'var(--md-surface-container-highest)',
                  color:
                    syncStatus === 'synced'
                      ? 'var(--md-on-success-container)'
                      : 'var(--md-on-surface-variant)',
                }}
              >
                {syncStatus === 'synced'
                  ? '● Synced'
                  : syncStatus === 'connecting'
                  ? 'Syncing...'
                  : syncStatus === 'signed-out'
                  ? 'Signed Out'
                  : 'Local Only'}
              </span>
            </div>

            {firebaseConfig ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <p style={{ fontSize: '12.5px', color: 'var(--md-on-surface-variant)' }}>
                  Project: <strong>{firebaseConfig.projectId}</strong>
                  {firebaseUser && (
                    <>
                      {' '}
                      • Signed in as{' '}
                      <strong>{firebaseUser.email || firebaseUser.displayName || 'Cloud User'}</strong>
                    </>
                  )}
                </p>

                {!firebaseUser ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                    <button
                      type="button"
                      onClick={() => void loginGoogle()}
                      className="m3-btn-filled"
                    >
                      <LogIn size={15} /> Sign in with Google
                    </button>
                    <button
                      type="button"
                      onClick={() => void loginAnonymousSync()}
                      className="m3-btn-tonal"
                    >
                      Anonymous Sync
                    </button>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                    <button
                      type="button"
                      onClick={() => void pushLocalToCloud()}
                      className="m3-btn-tonal"
                    >
                      <CloudUpload size={15} /> Push Local to Cloud
                    </button>
                    <button
                      type="button"
                      onClick={() => void logoutFirebase()}
                      className="m3-btn-tonal"
                    >
                      <LogOut size={15} /> Sign Out
                    </button>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => setShowConfigBox((v) => !v)}
                  style={{
                    alignSelf: 'flex-start',
                    fontSize: '12px',
                    color: 'var(--md-primary)',
                    fontWeight: 600,
                  }}
                >
                  {showConfigBox ? 'Hide config editor' : 'Change Firebase project config'}
                </button>
              </div>
            ) : (
              <p style={{ fontSize: '12.5px', color: 'var(--md-on-surface-variant)' }}>
                Data is saved locally on this device. To sync across Android and Web, paste your Firebase
                Web App config below (or set <code>.env.local</code>).
              </p>
            )}

            {(!firebaseConfig || showConfigBox) && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <textarea
                  rows={4}
                  value={snippetInput}
                  onChange={(e) => setSnippetInput(e.target.value)}
                  placeholder={`const firebaseConfig = {\n  apiKey: "AIza...",\n  authDomain: "your-app.firebaseapp.com",\n  projectId: "your-app"\n};`}
                  className="m3-textarea"
                  style={{ fontFamily: 'var(--font-mono), monospace', fontSize: '12px' }}
                />
                {configError && (
                  <p style={{ fontSize: '12px', color: 'var(--md-danger)' }}>{configError}</p>
                )}
                <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                  {firebaseConfig && (
                    <button
                      type="button"
                      onClick={() => void connectFirebaseConfig(null)}
                      className="m3-btn-tonal"
                    >
                      Clear Config
                    </button>
                  )}
                  <button type="button" onClick={() => void handleSaveSnippet()} className="m3-btn-filled">
                    Connect Firebase
                  </button>
                </div>
              </div>
            )}

            {syncError && (
              <p style={{ fontSize: '12px', color: 'var(--md-danger)' }}>{syncError}</p>
            )}
          </section>

          {/* 3. Data & Backup */}
          <section
            style={{
              background: 'var(--md-surface-container)',
              padding: '16px',
              borderRadius: '22px',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700 }}>
              <Database size={17} style={{ color: 'var(--md-primary)' }} />
              <span>Backup & Data</span>
            </div>

            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: '8px',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                <button type="button" onClick={handleDownloadBackup} className="m3-btn-tonal">
                  <Download size={14} /> Export JSON
                </button>
                <label className="m3-btn-tonal" style={{ cursor: 'pointer' }}>
                  <Upload size={14} /> Import JSON
                  <input
                    type="file"
                    accept="application/json"
                    onChange={handleUploadBackup}
                    style={{ display: 'none' }}
                  />
                </label>
              </div>

              <button
                type="button"
                onClick={() => {
                  if (window.confirm('Clear all gifts, people, and wishlist items to start fresh?')) {
                    resetData();
                    onClose();
                  }
                }}
                className="m3-btn-tonal"
                title="Clear all data to a clean slate"
              >
                <Trash2 size={14} /> Reset Data
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

function SparkleDot() {
  return (
    <span
      style={{
        width: '8px',
        height: '8px',
        borderRadius: '99px',
        background: 'currentColor',
        display: 'inline-block',
      }}
    />
  );
}
