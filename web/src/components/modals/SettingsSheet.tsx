'use client';

import React, { useEffect, useMemo, useState } from 'react';
import QRCode from 'qrcode';
import {
  Check,
  Cloud,
  CloudUpload,
  Copy,
  Database,
  Download,
  LogIn,
  LogOut,
  Moon,
  Palette,
  QrCode,
  Settings,
  Share2,
  Smartphone,
  Sun,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { useGiftStore } from '@/context/GiftStoreContext';
import {
  buildAndroidPairingDeepLink,
  buildWebPairingUrl,
  parseFirebaseSnippet,
} from '@/lib/firebase';
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
    showSnackbar,
  } = useGiftStore();

  const [snippetInput, setSnippetInput] = useState('');
  const [configError, setConfigError] = useState<string | null>(null);
  const [showConfigBox, setShowConfigBox] = useState(false);
  const [showPairPanel, setShowPairPanel] = useState(false);
  const [webClientIdDraft, setWebClientIdDraft] = useState('');
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [copiedPairLink, setCopiedPairLink] = useState(false);

  const effectivePairConfig = useMemo(() => {
    if (!firebaseConfig) return null;
    const trimmedClient = webClientIdDraft.trim() || firebaseConfig.webClientId?.trim();
    return trimmedClient ? { ...firebaseConfig, webClientId: trimmedClient } : firebaseConfig;
  }, [firebaseConfig, webClientIdDraft]);

  const webPairingUrl = useMemo(() => {
    if (!effectivePairConfig) return '';
    return buildWebPairingUrl(effectivePairConfig);
  }, [effectivePairConfig]);

  const androidDeepLink = useMemo(() => {
    if (!effectivePairConfig) return '';
    return buildAndroidPairingDeepLink(effectivePairConfig);
  }, [effectivePairConfig]);

  useEffect(() => {
    if (!open || !showPairPanel || !webPairingUrl) return;
    let cancelled = false;
    void QRCode.toDataURL(webPairingUrl, {
      width: 220,
      margin: 2,
      color: { dark: '#141218', light: '#ffffff' },
    })
      .then((url) => {
        if (!cancelled) setQrDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setQrDataUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [open, showPairPanel, webPairingUrl]);

  if (!open) return null;

  const handleCopyPairLink = async () => {
    if (!webPairingUrl || typeof navigator === 'undefined' || !navigator.clipboard) return;
    if (
      webClientIdDraft.trim() &&
      firebaseConfig &&
      webClientIdDraft.trim() !== (firebaseConfig.webClientId || '')
    ) {
      await connectFirebaseConfig({ ...firebaseConfig, webClientId: webClientIdDraft.trim() });
    }
    await navigator.clipboard.writeText(webPairingUrl);
    setCopiedPairLink(true);
    showSnackbar('Copied device pairing link to clipboard!');
    setTimeout(() => setCopiedPairLink(false), 2200);
  };

  const handleSharePairLink = async () => {
    if (!webPairingUrl) return;
    if (
      webClientIdDraft.trim() &&
      firebaseConfig &&
      webClientIdDraft.trim() !== (firebaseConfig.webClientId || '')
    ) {
      await connectFirebaseConfig({ ...firebaseConfig, webClientId: webClientIdDraft.trim() });
    }
    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: 'Gift Tracker — Cloud Sync Setup',
          text: 'Open this link to connect Gift Tracker and sign in with your own Google account:',
          url: webPairingUrl,
        });
        return;
      } catch {
        // Fallback to copy
      }
    }
    await handleCopyPairLink();
  };

  const handleSaveSnippet = async () => {
    const parsed = parseFirebaseSnippet(snippetInput);
    if (!parsed) {
      setConfigError(
        'Could not find apiKey and projectId. Paste firebaseConfig, google-services.json, or a pairing link.'
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
                    <button
                      type="button"
                      onClick={() => {
                        setWebClientIdDraft(firebaseConfig.webClientId || '');
                        setShowPairPanel((v) => !v);
                      }}
                      className="m3-btn-tonal"
                    >
                      <QrCode size={15} /> {showPairPanel ? 'Hide QR' : 'Pair Device / Share'}
                    </button>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                    <button
                      type="button"
                      onClick={() => {
                        setWebClientIdDraft(firebaseConfig.webClientId || '');
                        setShowPairPanel((v) => !v);
                      }}
                      className={showPairPanel ? 'm3-btn-filled' : 'm3-btn-tonal'}
                    >
                      <QrCode size={15} /> {showPairPanel ? 'Hide Pairing QR' : 'Pair Device / Invite'}
                    </button>
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

                {showPairPanel && (
                  <div
                    style={{
                      background: 'var(--md-surface-container-low)',
                      border: '1px solid var(--md-outline-variant)',
                      borderRadius: '18px',
                      padding: '14px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: '13.5px', fontWeight: 700 }}>
                        Pair Another Device or Invite a Friend
                      </div>
                      <p
                        style={{
                          fontSize: '12px',
                          color: 'var(--md-on-surface-variant)',
                          marginTop: '3px',
                          lineHeight: 1.45,
                        }}
                      >
                        Scan this QR code or open the link on another phone, tablet, or laptop to
                        configure Cloud Sync automatically. Anyone who signs in with their own Google
                        account gets a completely separate, private workspace.
                      </p>
                    </div>

                    {qrDataUrl && (
                      <div style={{ display: 'flex', justifyContent: 'center' }}>
                        <div
                          style={{
                            background: '#ffffff',
                            padding: '10px',
                            borderRadius: '16px',
                            display: 'inline-flex',
                            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.14)',
                          }}
                        >
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={qrDataUrl}
                            alt="Gift Tracker Firebase Pairing QR Code"
                            width={184}
                            height={184}
                            style={{ display: 'block', borderRadius: '8px' }}
                          />
                        </div>
                      </div>
                    )}

                    {!firebaseConfig.webClientId && (
                      <input
                        type="text"
                        value={webClientIdDraft}
                        onChange={(e) => setWebClientIdDraft(e.target.value)}
                        placeholder="Optional: Google OAuth Web Client ID (for Android Google Sign-In)"
                        className="m3-input"
                        style={{ fontSize: '12px', height: '36px' }}
                      />
                    )}

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                      <button
                        type="button"
                        onClick={() => void handleCopyPairLink()}
                        className="m3-btn-filled"
                      >
                        {copiedPairLink ? <Check size={14} /> : <Copy size={14} />}
                        {copiedPairLink ? 'Copied!' : 'Copy Pairing Link'}
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleSharePairLink()}
                        className="m3-btn-tonal"
                      >
                        <Share2 size={14} /> Share Link
                      </button>

                      {androidDeepLink && (
                        <a
                          href={androidDeepLink}
                          className="m3-btn-tonal"
                          style={{ textDecoration: 'none' }}
                        >
                          <Smartphone size={14} /> Open Android App
                        </a>
                      )}
                    </div>
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
                Data is saved locally on this device. To sync across Android and Web, paste a pairing
                link, <code>firebaseConfig</code>, or <code>google-services.json</code> below.
              </p>
            )}

            {(!firebaseConfig || showConfigBox) && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <textarea
                  rows={4}
                  value={snippetInput}
                  onChange={(e) => setSnippetInput(e.target.value)}
                  placeholder={`Paste a pairing link (?pair=...), firebaseConfig snippet, or google-services.json`}
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
