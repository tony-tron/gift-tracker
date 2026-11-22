import { initializeApp, getApps, deleteApp, type FirebaseApp } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  signInAnonymously,
  signOut as fbSignOut,
  onAuthStateChanged,
  type Auth,
  type User,
} from 'firebase/auth';
import {
  initializeFirestore,
  getFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  collection,
  doc,
  setDoc,
  deleteDoc,
  onSnapshot,
  writeBatch,
  getDocs,
  type Firestore,
  type Unsubscribe,
} from 'firebase/firestore';
import type {
  FirebaseClientConfig,
  GiftDataset,
  GiftEvent,
  GiftItem,
  Person,
  WishItem,
} from './types';

const RUNTIME_CONFIG_STORAGE_KEY = 'gift_tracker_firebase_config_v1';

let appInstance: FirebaseApp | null = null;
let authInstance: Auth | null = null;
let dbInstance: Firestore | null = null;
let activeConfigHash = '';

export function getEnvFirebaseConfig(): FirebaseClientConfig | null {
  const apiKey = process.env.NEXT_PUBLIC_FIREBASE_API_KEY;
  const authDomain = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN;
  const projectId = process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID;
  if (apiKey && authDomain && projectId) {
    return {
      apiKey,
      authDomain,
      projectId,
      storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
      messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
      appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
    };
  }
  return null;
}

export function getSavedFirebaseConfig(): FirebaseClientConfig | null {
  const envCfg = getEnvFirebaseConfig();
  if (envCfg) return envCfg;
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(RUNTIME_CONFIG_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<FirebaseClientConfig>;
    if (parsed.apiKey && parsed.projectId) {
      return {
        apiKey: parsed.apiKey.trim(),
        authDomain: (parsed.authDomain || `${parsed.projectId.trim()}.firebaseapp.com`).trim(),
        projectId: parsed.projectId.trim(),
        storageBucket: parsed.storageBucket?.trim(),
        messagingSenderId: parsed.messagingSenderId?.trim(),
        appId: parsed.appId?.trim(),
      };
    }
  } catch {
    return null;
  }
  return null;
}

export function parseFirebaseSnippet(input: string): FirebaseClientConfig | null {
  const trimmed = input.trim();
  if (!trimmed) return null;
  try {
    const direct = JSON.parse(trimmed) as Partial<FirebaseClientConfig>;
    if (direct.apiKey && direct.projectId) {
      return {
        apiKey: direct.apiKey,
        authDomain: direct.authDomain || `${direct.projectId}.firebaseapp.com`,
        projectId: direct.projectId,
        storageBucket: direct.storageBucket,
        messagingSenderId: direct.messagingSenderId,
        appId: direct.appId,
      };
    }
  } catch {
    // Parse JS object snippet from Firebase Console (e.g. const firebaseConfig = { apiKey: "...", ... })
    const extract = (key: string): string | undefined => {
      const re = new RegExp(`${key}\\s*:\\s*["'\`]([^"'\`]+)["'\`]`);
      const m = trimmed.match(re);
      return m ? m[1].trim() : undefined;
    };
    const apiKey = extract('apiKey');
    const projectId = extract('projectId');
    if (apiKey && projectId) {
      return {
        apiKey,
        authDomain: extract('authDomain') || `${projectId}.firebaseapp.com`,
        projectId,
        storageBucket: extract('storageBucket'),
        messagingSenderId: extract('messagingSenderId'),
        appId: extract('appId'),
      };
    }
  }
  return null;
}

export async function saveRuntimeFirebaseConfig(config: FirebaseClientConfig | null): Promise<void> {
  if (typeof window === 'undefined') return;
  if (!config) {
    window.localStorage.removeItem(RUNTIME_CONFIG_STORAGE_KEY);
  } else {
    window.localStorage.setItem(RUNTIME_CONFIG_STORAGE_KEY, JSON.stringify(config));
  }
  if (appInstance) {
    try {
      await deleteApp(appInstance);
    } catch {
      // ignore cleanup errors
    }
    appInstance = null;
    authInstance = null;
    dbInstance = null;
    activeConfigHash = '';
  }
}

export function initFirebaseServices(): {
  app: FirebaseApp;
  auth: Auth;
  db: Firestore;
  config: FirebaseClientConfig;
} | null {
  const config = getSavedFirebaseConfig();
  if (!config) return null;

  const hash = `${config.projectId}:${config.apiKey}`;
  if (appInstance && authInstance && dbInstance && activeConfigHash === hash) {
    return { app: appInstance, auth: authInstance, db: dbInstance, config };
  }

  const existing = getApps();
  const app = existing.length > 0 ? existing[0] : initializeApp(config);
  const auth = getAuth(app);

  let db: Firestore;
  try {
    if (typeof window !== 'undefined') {
      db = initializeFirestore(app, {
        localCache: persistentLocalCache({
          tabManager: persistentMultipleTabManager(),
        }),
      });
    } else {
      db = getFirestore(app);
    }
  } catch {
    db = getFirestore(app);
  }

  appInstance = app;
  authInstance = auth;
  dbInstance = db;
  activeConfigHash = hash;

  return { app, auth, db, config };
}

export async function signInWithGoogle(): Promise<User> {
  const services = initFirebaseServices();
  if (!services) throw new Error('Firebase is not configured yet.');
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  try {
    const cred = await signInWithPopup(services.auth, provider);
    return cred.user;
  } catch (err: unknown) {
    const code = (err as { code?: string })?.code || '';
    if (code === 'auth/popup-blocked' || code === 'auth/operation-not-supported-in-this-environment') {
      await signInWithRedirect(services.auth, provider);
      throw new Error('Redirecting to Google Sign-In...');
    }
    throw err;
  }
}

export async function signInQuickSync(): Promise<User> {
  const services = initFirebaseServices();
  if (!services) throw new Error('Firebase is not configured yet.');
  const cred = await signInAnonymously(services.auth);
  return cred.user;
}

export async function signOutFirebase(): Promise<void> {
  const services = initFirebaseServices();
  if (!services) return;
  await fbSignOut(services.auth);
}

export function observeAuth(callback: (user: User | null) => void): Unsubscribe | null {
  const services = initFirebaseServices();
  if (!services) return null;
  return onAuthStateChanged(services.auth, callback);
}

export async function checkRemoteHasData(db: Firestore, namespaceId: string): Promise<boolean> {
  const snap = await getDocs(collection(db, 'workspaces', namespaceId, 'people'));
  return !snap.empty;
}

export async function uploadFullDatasetToFirestore(
  db: Firestore,
  namespaceId: string,
  dataset: GiftDataset
): Promise<void> {
  // Firestore batches support up to 500 ops per batch
  const ops: Array<{ col: string; id: string; data: unknown }> = [
    ...dataset.people.map((p) => ({ col: 'people', id: p.id, data: p })),
    ...dataset.events.map((e) => ({ col: 'events', id: e.id, data: e })),
    ...dataset.gifts.map((g) => ({ col: 'gifts', id: g.id, data: g })),
    ...dataset.wishes.map((w) => ({ col: 'wishes', id: w.id, data: w })),
  ];

  const chunkSize = 400;
  for (let i = 0; i < ops.length; i += chunkSize) {
    const batch = writeBatch(db);
    const slice = ops.slice(i, i + chunkSize);
    for (const item of slice) {
      const ref = doc(db, 'workspaces', namespaceId, item.col, item.id);
      batch.set(ref, item.data as Record<string, unknown>);
    }
    await batch.commit();
  }
}

export async function upsertRemoteDoc(
  colName: 'people' | 'events' | 'gifts' | 'wishes',
  id: string,
  data: Person | GiftEvent | GiftItem | WishItem,
  namespaceId: string
): Promise<void> {
  const services = initFirebaseServices();
  if (!services || !namespaceId) return;
  const ref = doc(services.db, 'workspaces', namespaceId, colName, id);
  await setDoc(ref, JSON.parse(JSON.stringify(data)));
}

export async function deleteRemoteDoc(
  colName: 'people' | 'events' | 'gifts' | 'wishes',
  id: string,
  namespaceId: string
): Promise<void> {
  const services = initFirebaseServices();
  if (!services || !namespaceId) return;
  const ref = doc(services.db, 'workspaces', namespaceId, colName, id);
  await deleteDoc(ref);
}

export function subscribeToWorkspace(
  db: Firestore,
  namespaceId: string,
  handlers: {
    onPeople: (items: Person[]) => void;
    onEvents: (items: GiftEvent[]) => void;
    onGifts: (items: GiftItem[]) => void;
    onWishes: (items: WishItem[]) => void;
    onError?: (err: Error) => void;
  }
): Unsubscribe {
  const unsubs: Unsubscribe[] = [];

  unsubs.push(
    onSnapshot(
      collection(db, 'workspaces', namespaceId, 'people'),
      (snap) => {
        const items = snap.docs
          .map((d) => d.data() as Person)
          .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
        handlers.onPeople(items);
      },
      (err) => handlers.onError?.(err)
    )
  );

  unsubs.push(
    onSnapshot(
      collection(db, 'workspaces', namespaceId, 'events'),
      (snap) => {
        const items = snap.docs
          .map((d) => d.data() as GiftEvent)
          .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
        handlers.onEvents(items);
      },
      (err) => handlers.onError?.(err)
    )
  );

  unsubs.push(
    onSnapshot(
      collection(db, 'workspaces', namespaceId, 'gifts'),
      (snap) => {
        const items = snap.docs
          .map((d) => d.data() as GiftItem)
          .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''));
        handlers.onGifts(items);
      },
      (err) => handlers.onError?.(err)
    )
  );

  unsubs.push(
    onSnapshot(
      collection(db, 'workspaces', namespaceId, 'wishes'),
      (snap) => {
        const items = snap.docs.map((d) => d.data() as WishItem);
        handlers.onWishes(items);
      },
      (err) => handlers.onError?.(err)
    )
  );

  return () => {
    unsubs.forEach((u) => u());
  };
}
