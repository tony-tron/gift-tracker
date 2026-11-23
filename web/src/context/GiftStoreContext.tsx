'use client';

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { User } from 'firebase/auth';
import { EMPTY_DATASET } from '@/lib/seedData';
import {
  checkRemoteHasData,
  deleteRemoteDoc,
  getSavedFirebaseConfig,
  initFirebaseServices,
  observeAuth,
  saveRuntimeFirebaseConfig,
  signInQuickSync,
  signInWithGoogle,
  signOutFirebase,
  subscribeToWorkspace,
  uploadFullDatasetToFirestore,
  upsertRemoteDoc,
} from '@/lib/firebase';
import type {
  AvatarShape,
  AvatarTone,
  ColorMode,
  FirebaseClientConfig,
  GiftDataset,
  GiftEvent,
  GiftItem,
  NavTab,
  Person,
  ThemeSeed,
  WishItem,
} from '@/lib/types';

const LOCAL_DATA_KEY = 'gift_tracker_dataset_v1';
const THEME_PREF_KEY = 'gift_tracker_theme_v1';

export type SyncStatus =
  | 'local-only'
  | 'signed-out'
  | 'connecting'
  | 'synced'
  | 'error';

export interface SnackbarMessage {
  id: string;
  text: string;
  actionLabel?: string;
  onAction?: () => void;
}

interface GiftStoreContextValue {
  people: Person[];
  events: GiftEvent[];
  gifts: GiftItem[];
  wishes: WishItem[];

  // Navigation & Active selections
  activeTab: NavTab;
  setActiveTab: (tab: NavTab) => void;
  selectedEventId: string;
  setSelectedEventId: (id: string) => void;
  selectedPersonId: string | null;
  setSelectedPersonId: (id: string | null) => void;

  // Material You Expressive Theme
  themeSeed: ThemeSeed;
  setThemeSeed: (seed: ThemeSeed) => void;
  colorMode: ColorMode;
  setColorMode: (mode: ColorMode) => void;

  // Firebase Cloud Sync
  syncStatus: SyncStatus;
  syncError: string | null;
  firebaseUser: User | null;
  firebaseConfig: FirebaseClientConfig | null;
  connectFirebaseConfig: (cfg: FirebaseClientConfig | null) => Promise<void>;
  loginGoogle: () => Promise<void>;
  loginAnonymousSync: () => Promise<void>;
  logoutFirebase: () => Promise<void>;
  pushLocalToCloud: () => Promise<void>;

  // Gift CRUD & Expressive Workflows
  addGift: (input: Omit<GiftItem, 'id' | 'createdAt'>) => GiftItem;
  updateGift: (id: string, patch: Partial<GiftItem>) => void;
  deleteGift: (id: string) => void;
  toggleGiftStatus: (id: string, field: 'bought' | 'received' | 'wrapped') => void;
  moveGiftToEvent: (giftId: string, eventId: string, personId?: string | null) => void;
  moveGiftToIdeas: (giftId: string, saveForLater?: boolean) => void;

  // People CRUD
  addPerson: (
    input: Omit<Person, 'id' | 'order' | 'avatarShape' | 'avatarTone'> & {
      avatarShape?: AvatarShape;
      avatarTone?: AvatarTone;
    }
  ) => Person;
  updatePerson: (id: string, patch: Partial<Person>) => void;
  deletePerson: (id: string) => void;
  reorderPeople: (orderedIds: string[]) => void;

  // Event CRUD
  addEvent: (input: Omit<GiftEvent, 'id' | 'order'>) => GiftEvent;
  updateEvent: (id: string, patch: Partial<GiftEvent>) => void;
  deleteEvent: (id: string) => void;

  // Personal Wishlist CRUD
  addWish: (input: Omit<WishItem, 'id'>) => WishItem;
  updateWish: (id: string, patch: Partial<WishItem>) => void;
  deleteWish: (id: string) => void;

  // Reset & Backup
  resetData: () => void;
  exportJsonBackup: () => string;
  importJsonBackup: (raw: string) => boolean;

  // Snackbar notifications
  snackbar: SnackbarMessage | null;
  showSnackbar: (text: string, actionLabel?: string, onAction?: () => void) => void;
  dismissSnackbar: () => void;
}

const GiftStoreContext = createContext<GiftStoreContextValue | null>(null);

const AVATAR_SHAPES: AvatarShape[] = ['squircle', 'cookie', 'clover', 'arch', 'pill', 'flower'];
const AVATAR_TONES: AvatarTone[] = ['terracotta', 'sage', 'amber', 'teal', 'rose', 'ochre'];

function makeId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
}

function readInitialDataset(): GiftDataset {
  if (typeof window === 'undefined') return EMPTY_DATASET;
  try {
    const savedRaw = window.localStorage.getItem(LOCAL_DATA_KEY);
    if (savedRaw) {
      const parsed = JSON.parse(savedRaw) as Partial<GiftDataset>;
      if (parsed.people && parsed.events && parsed.gifts && parsed.wishes) {
        return {
          people: [...parsed.people].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
          events: parsed.events,
          gifts: parsed.gifts,
          wishes: parsed.wishes,
        };
      }
    }
  } catch {
    // Fallback to empty dataset
  }
  return EMPTY_DATASET;
}

function readInitialTheme(): { seed: ThemeSeed; mode: ColorMode } {
  if (typeof window === 'undefined') return { seed: 'terracotta', mode: 'dark' };
  try {
    const raw = window.localStorage.getItem(THEME_PREF_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as { seed?: ThemeSeed; mode?: ColorMode };
      return {
        seed: parsed.seed || 'terracotta',
        mode: parsed.mode || 'dark',
      };
    }
  } catch {
    // ignore
  }
  return { seed: 'terracotta', mode: 'dark' };
}

export function GiftStoreProvider({ children }: { children: React.ReactNode }) {
  const [dataset, setDataset] = useState<GiftDataset>(readInitialDataset);

  const [activeTab, setActiveTab] = useState<NavTab>(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      if (params.get('action') === 'new-idea') return 'ideas';
      const tab = params.get('tab') as NavTab | null;
      if (tab && ['events', 'ideas', 'people', 'wishlist'].includes(tab)) return tab;
    }
    return 'events';
  });

  const [selectedEventId, setSelectedEventId] = useState<string>(() => {
    if (typeof window !== 'undefined') {
      const ev = new URLSearchParams(window.location.search).get('event');
      if (ev) return ev;
    }
    return '2026-christmas';
  });

  const [selectedPersonId, setSelectedPersonId] = useState<string | null>(null);

  const [themeSeed, setThemeSeed] = useState<ThemeSeed>(() => readInitialTheme().seed);
  const [colorMode, setColorMode] = useState<ColorMode>(() => readInitialTheme().mode);

  const [firebaseConfig, setFirebaseConfig] = useState<FirebaseClientConfig | null>(() =>
    getSavedFirebaseConfig()
  );
  const [firebaseUser, setFirebaseUser] = useState<User | null>(null);
  const [remoteSyncState, setRemoteSyncState] = useState<SyncStatus>('signed-out');
  const [syncError, setSyncError] = useState<string | null>(null);

  const [snackbar, setSnackbar] = useState<SnackbarMessage | null>(null);
  const snackbarTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const datasetRef = useRef<GiftDataset>(dataset);

  const syncStatus: SyncStatus = firebaseConfig ? remoteSyncState : 'local-only';

  const showSnackbar = useCallback(
    (text: string, actionLabel?: string, onAction?: () => void) => {
      if (snackbarTimerRef.current) clearTimeout(snackbarTimerRef.current);
      setSnackbar({ id: makeId('msg'), text, actionLabel, onAction });
      snackbarTimerRef.current = setTimeout(() => {
        setSnackbar(null);
      }, 4500);
    },
    []
  );

  const dismissSnackbar = useCallback(() => {
    if (snackbarTimerRef.current) clearTimeout(snackbarTimerRef.current);
    setSnackbar(null);
  }, []);

  // Persist dataset locally & keep latest ref synchronized
  useEffect(() => {
    datasetRef.current = dataset;
    try {
      window.localStorage.setItem(LOCAL_DATA_KEY, JSON.stringify(dataset));
    } catch {
      // Ignore quota errors
    }
  }, [dataset]);

  // Apply Material You Dynamic Color seed & light/dark mode to <html>
  useEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-seed', themeSeed);

    const applyMode = () => {
      if (colorMode === 'system') {
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        root.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
      } else {
        root.setAttribute('data-theme', colorMode);
      }
    };
    applyMode();

    try {
      window.localStorage.setItem(
        THEME_PREF_KEY,
        JSON.stringify({ seed: themeSeed, mode: colorMode })
      );
    } catch {
      // ignore
    }

    if (colorMode === 'system') {
      const mql = window.matchMedia('(prefers-color-scheme: dark)');
      mql.addEventListener('change', applyMode);
      return () => mql.removeEventListener('change', applyMode);
    }
  }, [themeSeed, colorMode]);

  // Observe Firebase Auth & real-time Firestore workspace sync
  useEffect(() => {
    if (!firebaseConfig) return;
    const services = initFirebaseServices();
    if (!services) return;

    let unsubFirestore: (() => void) | null = null;

    const unsubAuth = observeAuth(async (user) => {
      if (unsubFirestore) {
        unsubFirestore();
        unsubFirestore = null;
      }
      setFirebaseUser(user);

      if (!user) {
        setRemoteSyncState('signed-out');
        return;
      }

      setRemoteSyncState('connecting');
      setSyncError(null);

      try {
        const hasRemote = await checkRemoteHasData(services.db, user.uid);
        if (!hasRemote) {
          await uploadFullDatasetToFirestore(services.db, user.uid, datasetRef.current);
        }

        unsubFirestore = subscribeToWorkspace(services.db, user.uid, {
          onPeople: (people) => {
            if (people.length > 0) {
              setDataset((prev) => ({ ...prev, people }));
            }
          },
          onEvents: (events) => {
            if (events.length > 0) {
              setDataset((prev) => ({ ...prev, events }));
            }
          },
          onGifts: (gifts) => {
            setDataset((prev) => ({ ...prev, gifts }));
          },
          onWishes: (wishes) => {
            setDataset((prev) => ({ ...prev, wishes }));
          },
          onError: (err) => {
            setSyncError(err.message);
            setRemoteSyncState('error');
          },
        });

        setRemoteSyncState('synced');
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to sync with Firestore';
        setSyncError(message);
        setRemoteSyncState('error');
      }
    });

    return () => {
      if (unsubFirestore) unsubFirestore();
      if (unsubAuth) unsubAuth();
    };
  }, [firebaseConfig]);

  // Firebase Auth & Config Actions
  const connectFirebaseConfig = useCallback(
    async (cfg: FirebaseClientConfig | null) => {
      await saveRuntimeFirebaseConfig(cfg);
      setFirebaseConfig(getSavedFirebaseConfig());
      setFirebaseUser(null);
      setRemoteSyncState('signed-out');
      setSyncError(null);
      if (cfg) {
        showSnackbar('Firebase project connected! Sign in to start real-time sync.');
      } else {
        showSnackbar('Disconnected runtime Firebase configuration.');
      }
    },
    [showSnackbar]
  );

  const loginGoogle = useCallback(async () => {
    setSyncError(null);
    try {
      await signInWithGoogle();
      showSnackbar('Signed in with Google — syncing across your devices!');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Sign-in failed';
      setSyncError(msg);
      throw err;
    }
  }, [showSnackbar]);

  const loginAnonymousSync = useCallback(async () => {
    setSyncError(null);
    try {
      await signInQuickSync();
      showSnackbar('Cloud sync active!');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Anonymous sign-in failed';
      setSyncError(msg);
      throw err;
    }
  }, [showSnackbar]);

  const logoutFirebase = useCallback(async () => {
    await signOutFirebase();
    setFirebaseUser(null);
    setRemoteSyncState('signed-out');
    showSnackbar('Signed out of Cloud Sync (local data preserved).');
  }, [showSnackbar]);

  const pushLocalToCloud = useCallback(async () => {
    const services = initFirebaseServices();
    if (!services || !firebaseUser) return;
    setRemoteSyncState('connecting');
    try {
      await uploadFullDatasetToFirestore(services.db, firebaseUser.uid, datasetRef.current);
      setRemoteSyncState('synced');
      showSnackbar('All people, sizes, events, and gifts synced to Firebase!');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Cloud upload failed';
      setSyncError(msg);
      setRemoteSyncState('error');
    }
  }, [firebaseUser, showSnackbar]);

  // Gift CRUD
  const addGift = useCallback(
    (input: Omit<GiftItem, 'id' | 'createdAt'>): GiftItem => {
      const item: GiftItem = {
        ...input,
        id: makeId('gift'),
        createdAt: new Date().toISOString(),
      };
      setDataset((prev) => ({ ...prev, gifts: [item, ...prev.gifts] }));
      if (firebaseUser) {
        void upsertRemoteDoc('gifts', item.id, item, firebaseUser.uid);
      }
      showSnackbar(
        item.eventId ? `Added "${item.title}" to event` : `Saved "${item.title}" to Ideas`
      );
      return item;
    },
    [firebaseUser, showSnackbar]
  );

  const updateGift = useCallback(
    (id: string, patch: Partial<GiftItem>) => {
      setDataset((prev) => {
        const nextGifts = prev.gifts.map((g) => {
          if (g.id !== id) return g;
          const updated = { ...g, ...patch };
          if (firebaseUser) {
            void upsertRemoteDoc('gifts', id, updated, firebaseUser.uid);
          }
          return updated;
        });
        return { ...prev, gifts: nextGifts };
      });
    },
    [firebaseUser]
  );

  const deleteGift = useCallback(
    (id: string) => {
      const target = datasetRef.current.gifts.find((g) => g.id === id);
      setDataset((prev) => ({
        ...prev,
        gifts: prev.gifts.filter((g) => g.id !== id),
      }));
      if (firebaseUser) {
        void deleteRemoteDoc('gifts', id, firebaseUser.uid);
      }
      if (target) {
        showSnackbar(`Removed "${target.title}"`, 'Undo', () => {
          setDataset((prev) => ({ ...prev, gifts: [target, ...prev.gifts] }));
          if (firebaseUser) {
            void upsertRemoteDoc('gifts', target.id, target, firebaseUser.uid);
          }
        });
      }
    },
    [firebaseUser, showSnackbar]
  );

  const toggleGiftStatus = useCallback(
    (id: string, field: 'bought' | 'received' | 'wrapped') => {
      setDataset((prev) => {
        const nextGifts = prev.gifts.map((g) => {
          if (g.id !== id) return g;
          const nextVal = !g[field];
          const patch: Partial<GiftItem> = { [field]: nextVal };
          if (nextVal && (field === 'received' || field === 'wrapped')) {
            patch.bought = true;
          }
          if (!nextVal && field === 'bought') {
            patch.received = false;
            patch.wrapped = false;
          }
          const updated = { ...g, ...patch };
          if (firebaseUser) {
            void upsertRemoteDoc('gifts', id, updated, firebaseUser.uid);
          }
          return updated;
        });
        return { ...prev, gifts: nextGifts };
      });
    },
    [firebaseUser]
  );

  const moveGiftToEvent = useCallback(
    (giftId: string, eventId: string, personId?: string | null) => {
      const targetEvent = datasetRef.current.events.find((e) => e.id === eventId);
      const prevGift = datasetRef.current.gifts.find((g) => g.id === giftId);
      setDataset((prev) => {
        const nextGifts = prev.gifts.map((g) => {
          if (g.id !== giftId) return g;
          const updated: GiftItem = {
            ...g,
            eventId,
            personId: personId !== undefined ? personId : g.personId,
            saveForLater: false,
          };
          if (firebaseUser) {
            void upsertRemoteDoc('gifts', giftId, updated, firebaseUser.uid);
          }
          return updated;
        });
        return { ...prev, gifts: nextGifts };
      });
      if (prevGift && targetEvent) {
        showSnackbar(
          `Moved "${prevGift.title}" to ${targetEvent.name}`,
          'Undo',
          () => {
            updateGift(giftId, {
              eventId: prevGift.eventId,
              personId: prevGift.personId,
              saveForLater: prevGift.saveForLater,
            });
          }
        );
      }
    },
    [firebaseUser, showSnackbar, updateGift]
  );

  const moveGiftToIdeas = useCallback(
    (giftId: string, saveForLater = false) => {
      const prevGift = datasetRef.current.gifts.find((g) => g.id === giftId);
      updateGift(giftId, { eventId: null, saveForLater });
      if (prevGift) {
        showSnackbar(
          saveForLater
            ? `Saved "${prevGift.title}" to Stockpile for later`
            : `Moved "${prevGift.title}" back to Ideas Backlog`,
          'Undo',
          () => {
            updateGift(giftId, {
              eventId: prevGift.eventId,
              saveForLater: prevGift.saveForLater,
            });
          }
        );
      }
    },
    [showSnackbar, updateGift]
  );

  // People CRUD
  const addPerson = useCallback(
    (
      input: Omit<Person, 'id' | 'order' | 'avatarShape' | 'avatarTone'> & {
        avatarShape?: AvatarShape;
        avatarTone?: AvatarTone;
      }
    ): Person => {
      const idx = datasetRef.current.people.length;
      const person: Person = {
        id: makeId(
          input.name
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-|-$/g, '') || 'person'
        ),
        name: input.name.trim(),
        budget: input.budget,
        relationship: input.relationship || 'Family & Close Circle',
        sizes: input.sizes || {},
        notes: input.notes || '',
        birthday: input.birthday,
        avatarShape: input.avatarShape || AVATAR_SHAPES[idx % AVATAR_SHAPES.length],
        avatarTone: input.avatarTone || AVATAR_TONES[idx % AVATAR_TONES.length],
        order: idx,
      };
      setDataset((prev) => ({ ...prev, people: [...prev.people, person] }));
      if (firebaseUser) {
        void upsertRemoteDoc('people', person.id, person, firebaseUser.uid);
      }
      setSelectedPersonId(person.id);
      showSnackbar(`Added ${person.name} to People`);
      return person;
    },
    [firebaseUser, showSnackbar]
  );

  const updatePerson = useCallback(
    (id: string, patch: Partial<Person>) => {
      setDataset((prev) => {
        const nextPeople = prev.people.map((p) => {
          if (p.id !== id) return p;
          const updated = { ...p, ...patch };
          if (firebaseUser) {
            void upsertRemoteDoc('people', id, updated, firebaseUser.uid);
          }
          return updated;
        });
        return { ...prev, people: nextPeople };
      });
    },
    [firebaseUser]
  );

  const deletePerson = useCallback(
    (id: string) => {
      const target = datasetRef.current.people.find((p) => p.id === id);
      setDataset((prev) => ({
        ...prev,
        people: prev.people.filter((p) => p.id !== id),
        gifts: prev.gifts.map((g) => (g.personId === id ? { ...g, personId: null } : g)),
      }));
      if (firebaseUser) {
        void deleteRemoteDoc('people', id, firebaseUser.uid);
      }
      if (selectedPersonId === id) {
        const remaining = datasetRef.current.people.filter((p) => p.id !== id);
        setSelectedPersonId(remaining[0]?.id ?? null);
      }
      if (target) {
        showSnackbar(`Removed ${target.name} from People`);
      }
    },
    [firebaseUser, selectedPersonId, showSnackbar]
  );

  const reorderPeople = useCallback(
    (orderedIds: string[]) => {
      setDataset((prev) => {
        const idMap = new Map(prev.people.map((p) => [p.id, p]));
        const nextPeople: Person[] = [];
        orderedIds.forEach((id, index) => {
          const p = idMap.get(id);
          if (p) {
            const updated = { ...p, order: index };
            nextPeople.push(updated);
            if (firebaseUser) {
              void upsertRemoteDoc('people', id, updated, firebaseUser.uid);
            }
          }
        });
        prev.people.forEach((p) => {
          if (!orderedIds.includes(p.id)) {
            const updated = { ...p, order: nextPeople.length };
            nextPeople.push(updated);
            if (firebaseUser) {
              void upsertRemoteDoc('people', p.id, updated, firebaseUser.uid);
            }
          }
        });
        return { ...prev, people: nextPeople };
      });
      showSnackbar('Updated people order');
    },
    [firebaseUser, showSnackbar]
  );

  // Events CRUD
  const addEvent = useCallback(
    (input: Omit<GiftEvent, 'id' | 'order'>): GiftEvent => {
      const slug = `${input.year}-${input.name
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '')}`;
      const ev: GiftEvent = {
        ...input,
        id: datasetRef.current.events.some((e) => e.id === slug) ? makeId('event') : slug,
        order: 0,
      };
      setDataset((prev) => ({
        ...prev,
        events: [ev, ...prev.events.map((e, i) => ({ ...e, order: i + 1 }))],
      }));
      if (firebaseUser) {
        void upsertRemoteDoc('events', ev.id, ev, firebaseUser.uid);
      }
      setSelectedEventId(ev.id);
      showSnackbar(`Created occasion "${ev.name}"`);
      return ev;
    },
    [firebaseUser, showSnackbar]
  );

  const updateEvent = useCallback(
    (id: string, patch: Partial<GiftEvent>) => {
      setDataset((prev) => {
        const nextEvents = prev.events.map((e) => {
          if (e.id !== id) return e;
          const updated = { ...e, ...patch };
          if (firebaseUser) {
            void upsertRemoteDoc('events', id, updated, firebaseUser.uid);
          }
          return updated;
        });
        return { ...prev, events: nextEvents };
      });
    },
    [firebaseUser]
  );

  const deleteEvent = useCallback(
    (id: string) => {
      setDataset((prev) => ({
        ...prev,
        events: prev.events.filter((e) => e.id !== id),
        gifts: prev.gifts.map((g) => (g.eventId === id ? { ...g, eventId: null } : g)),
      }));
      if (firebaseUser) {
        void deleteRemoteDoc('events', id, firebaseUser.uid);
      }
      if (selectedEventId === id) {
        setSelectedEventId('2026-christmas');
      }
    },
    [firebaseUser, selectedEventId]
  );

  // Wishes CRUD
  const addWish = useCallback(
    (input: Omit<WishItem, 'id'>): WishItem => {
      const wish: WishItem = {
        ...input,
        id: makeId('wish'),
      };
      setDataset((prev) => ({ ...prev, wishes: [wish, ...prev.wishes] }));
      if (firebaseUser) {
        void upsertRemoteDoc('wishes', wish.id, wish, firebaseUser.uid);
      }
      showSnackbar(`Added "${wish.title}" to My Wishlist`);
      return wish;
    },
    [firebaseUser, showSnackbar]
  );

  const updateWish = useCallback(
    (id: string, patch: Partial<WishItem>) => {
      setDataset((prev) => {
        const nextWishes = prev.wishes.map((w) => {
          if (w.id !== id) return w;
          const updated = { ...w, ...patch };
          if (firebaseUser) {
            void upsertRemoteDoc('wishes', id, updated, firebaseUser.uid);
          }
          return updated;
        });
        return { ...prev, wishes: nextWishes };
      });
    },
    [firebaseUser]
  );

  const deleteWish = useCallback(
    (id: string) => {
      setDataset((prev) => ({
        ...prev,
        wishes: prev.wishes.filter((w) => w.id !== id),
      }));
      if (firebaseUser) {
        void deleteRemoteDoc('wishes', id, firebaseUser.uid);
      }
      showSnackbar('Removed item from Wishlist');
    },
    [firebaseUser, showSnackbar]
  );

  // Reset to clean slate
  const resetData = useCallback(() => {
    setDataset(EMPTY_DATASET);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(LOCAL_DATA_KEY, JSON.stringify(EMPTY_DATASET));
    }
    if (firebaseUser) {
      const services = initFirebaseServices();
      if (services) {
        void uploadFullDatasetToFirestore(services.db, firebaseUser.uid, EMPTY_DATASET);
      }
    }
    showSnackbar('Cleared local data to clean slate.');
  }, [firebaseUser, showSnackbar]);

  const exportJsonBackup = useCallback((): string => {
    return JSON.stringify(datasetRef.current, null, 2);
  }, []);

  const importJsonBackup = useCallback(
    (raw: string): boolean => {
      try {
        const parsed = JSON.parse(raw) as Partial<GiftDataset>;
        if (!parsed.people || !parsed.events || !parsed.gifts || !parsed.wishes) {
          return false;
        }
        const clean: GiftDataset = {
          people: parsed.people,
          events: parsed.events,
          gifts: parsed.gifts,
          wishes: parsed.wishes,
        };
        setDataset(clean);
        if (firebaseUser) {
          const services = initFirebaseServices();
          if (services) {
            void uploadFullDatasetToFirestore(services.db, firebaseUser.uid, clean);
          }
        }
        showSnackbar('Imported backup successfully!');
        return true;
      } catch {
        return false;
      }
    },
    [firebaseUser, showSnackbar]
  );

  const value = useMemo<GiftStoreContextValue>(
    () => ({
      people: dataset.people,
      events: dataset.events,
      gifts: dataset.gifts,
      wishes: dataset.wishes,
      activeTab,
      setActiveTab,
      selectedEventId,
      setSelectedEventId,
      selectedPersonId,
      setSelectedPersonId,
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
      addGift,
      updateGift,
      deleteGift,
      toggleGiftStatus,
      moveGiftToEvent,
      moveGiftToIdeas,
      addPerson,
      updatePerson,
      deletePerson,
      reorderPeople,
      addEvent,
      updateEvent,
      deleteEvent,
      addWish,
      updateWish,
      deleteWish,
      resetData,
      exportJsonBackup,
      importJsonBackup,
      snackbar,
      showSnackbar,
      dismissSnackbar,
    }),
    [
      dataset,
      activeTab,
      selectedEventId,
      selectedPersonId,
      themeSeed,
      colorMode,
      syncStatus,
      syncError,
      firebaseUser,
      firebaseConfig,
      connectFirebaseConfig,
      loginGoogle,
      loginAnonymousSync,
      logoutFirebase,
      pushLocalToCloud,
      addGift,
      updateGift,
      deleteGift,
      toggleGiftStatus,
      moveGiftToEvent,
      moveGiftToIdeas,
      addPerson,
      updatePerson,
      deletePerson,
      reorderPeople,
      addEvent,
      updateEvent,
      deleteEvent,
      addWish,
      updateWish,
      deleteWish,
      resetData,
      exportJsonBackup,
      importJsonBackup,
      snackbar,
      showSnackbar,
      dismissSnackbar,
    ]
  );

  return <GiftStoreContext.Provider value={value}>{children}</GiftStoreContext.Provider>;
}

export function useGiftStore(): GiftStoreContextValue {
  const ctx = useContext(GiftStoreContext);
  if (!ctx) {
    throw new Error('useGiftStore must be used inside GiftStoreProvider');
  }
  return ctx;
}
