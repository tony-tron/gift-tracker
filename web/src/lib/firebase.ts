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
  if (apiKey && projectId) {
    return {
      apiKey,
      authDomain: authDomain || `${projectId}.firebaseapp.com`,
      projectId,
      storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
      messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
      appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
      webClientId: process.env.NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID,
    };
  }
  return null;
}

export function getSavedFirebaseConfig(): FirebaseClientConfig | null {
  let runtimeParsed: Partial<FirebaseClientConfig> | null = null;
  if (typeof window !== 'undefined') {
    try {
      const raw = window.localStorage.getItem(RUNTIME_CONFIG_STORAGE_KEY);
      if (raw) {
        runtimeParsed = JSON.parse(raw) as Partial<FirebaseClientConfig>;
      }
    } catch {
      runtimeParsed = null;
    }
  }

  const envCfg = getEnvFirebaseConfig();
  if (envCfg) {
    // Allow optional runtime webClientId override if set on the same project
    if (
      runtimeParsed &&
      (!runtimeParsed.projectId || runtimeParsed.projectId.trim() === envCfg.projectId) &&
      runtimeParsed.webClientId?.trim() &&
      !envCfg.webClientId
    ) {
      return { ...envCfg, webClientId: runtimeParsed.webClientId.trim() };
    }
    return envCfg;
  }

  if (runtimeParsed?.apiKey && runtimeParsed?.projectId) {
    return {
      apiKey: runtimeParsed.apiKey.trim(),
      authDomain: (runtimeParsed.authDomain || `${runtimeParsed.projectId.trim()}.firebaseapp.com`).trim(),
      projectId: runtimeParsed.projectId.trim(),
      storageBucket: runtimeParsed.storageBucket?.trim(),
      messagingSenderId: runtimeParsed.messagingSenderId?.trim(),
      appId: runtimeParsed.appId?.trim(),
      webClientId: runtimeParsed.webClientId?.trim(),
    };
  }
  return null;
}

function toBase64Url(str: string): string {
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromBase64Url(token: string): string | null {
  try {
    const normalized = token.trim().replace(/-/g, '+').replace(/_/g, '/');
    const padLen = (4 - (normalized.length % 4)) % 4;
    const padded = normalized + '='.repeat(padLen);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new TextDecoder().decode(bytes);
  } catch {
    return null;
  }
}

export interface DecodedPairingPayload {
  config: FirebaseClientConfig;
  webUrl?: string;
}

export function encodeFirebasePairingToken(config: FirebaseClientConfig, webUrl?: string): string {
  const payload: Record<string, unknown> = {
    v: 1,
    apiKey: config.apiKey.trim(),
    projectId: config.projectId.trim(),
    authDomain: (config.authDomain || `${config.projectId.trim()}.firebaseapp.com`).trim(),
  };
  if (config.storageBucket?.trim()) payload.storageBucket = config.storageBucket.trim();
  if (config.messagingSenderId?.trim()) payload.messagingSenderId = config.messagingSenderId.trim();
  if (config.appId?.trim()) payload.appId = config.appId.trim();
  if (config.webClientId?.trim()) payload.webClientId = config.webClientId.trim();
  if (webUrl?.trim()) payload.webUrl = webUrl.trim();
  return toBase64Url(JSON.stringify(payload));
}

export function decodeFirebasePairingToken(rawInput: string): DecodedPairingPayload | null {
  const trimmed = rawInput.trim();
  if (!trimmed) return null;

  let candidate = trimmed;
  let inferredWebUrl: string | undefined;

  if (/^(https?:\/\/|gifttracker:\/\/)/i.test(trimmed)) {
    try {
      const url = new URL(trimmed);
      const param = url.searchParams.get('pair') || url.searchParams.get('cfg');
      if (!param) return null;
      candidate = param;
      if (url.protocol === 'http:' || url.protocol === 'https:') {
        inferredWebUrl = url.origin;
      }
    } catch {
      return null;
    }
  }

  const decodedJson = fromBase64Url(candidate);
  if (!decodedJson) return null;

  try {
    const parsed = JSON.parse(decodedJson) as Record<string, unknown>;
    const apiKey = typeof parsed.apiKey === 'string' ? parsed.apiKey.trim() : '';
    const projectId = typeof parsed.projectId === 'string' ? parsed.projectId.trim() : '';
    if (!apiKey || !projectId) return null;

    const str = (key: string): string | undefined => {
      const v = parsed[key];
      return typeof v === 'string' && v.trim() ? v.trim() : undefined;
    };

    return {
      config: {
        apiKey,
        projectId,
        authDomain: str('authDomain') || `${projectId}.firebaseapp.com`,
        storageBucket: str('storageBucket'),
        messagingSenderId: str('messagingSenderId') || str('gcmSenderId'),
        appId: str('appId') || str('applicationId'),
        webClientId: str('webClientId'),
      },
      webUrl: str('webUrl') || inferredWebUrl,
    };
  } catch {
    return null;
  }
}

export function buildWebPairingUrl(config: FirebaseClientConfig, originOverride?: string): string {
  const origin =
    originOverride?.replace(/\/+$/, '') ||
    (typeof window !== 'undefined' ? window.location.origin : 'https://localhost:3000');
  const token = encodeFirebasePairingToken(config, origin);
  return `${origin}/?pair=${encodeURIComponent(token)}`;
}

export function buildAndroidPairingDeepLink(
  config: FirebaseClientConfig,
  originOverride?: string
): string {
  const origin =
    originOverride?.replace(/\/+$/, '') ||
    (typeof window !== 'undefined' ? window.location.origin : undefined);
  const token = encodeFirebasePairingToken(config, origin);
  return `gifttracker://pair?cfg=${encodeURIComponent(token)}`;
}

export function parseFirebaseSnippet(input: string): FirebaseClientConfig | null {
  const trimmed = input.trim();
  if (!trimmed) return null;

  // 1. Check if input is a pairing URL or base64url pairing token
  const fromToken = decodeFirebasePairingToken(trimmed);
  if (fromToken) return fromToken.config;

  try {
    const rawObj = JSON.parse(trimmed) as Record<string, unknown>;

    // 2. Check if Android google-services.json was pasted directly
    if (
      rawObj.project_info &&
      typeof rawObj.project_info === 'object' &&
      Array.isArray(rawObj.client)
    ) {
      const pInfo = rawObj.project_info as Record<string, unknown>;
      const projectId = typeof pInfo.project_id === 'string' ? pInfo.project_id.trim() : '';
      const storageBucket =
        typeof pInfo.storage_bucket === 'string' ? pInfo.storage_bucket.trim() : undefined;
      const messagingSenderId =
        typeof pInfo.project_number === 'string' ? pInfo.project_number.trim() : undefined;
      const firstClient = rawObj.client[0] as Record<string, unknown> | undefined;
      const clientInfo = firstClient?.client_info as Record<string, unknown> | undefined;
      const appId =
        typeof clientInfo?.mobilesdk_app_id === 'string'
          ? clientInfo.mobilesdk_app_id.trim()
          : undefined;
      const apiKeyArr = Array.isArray(firstClient?.api_key) ? firstClient.api_key : [];
      const apiKeyObj = apiKeyArr[0] as Record<string, unknown> | undefined;
      const apiKey =
        typeof apiKeyObj?.current_key === 'string' ? apiKeyObj.current_key.trim() : '';

      let webClientId: string | undefined;
      for (const c of rawObj.client as Array<Record<string, unknown>>) {
        const oauthList = Array.isArray(c?.oauth_client)
          ? (c.oauth_client as Array<Record<string, unknown>>)
          : [];
        const found = oauthList.find((o) => String(o?.client_type) === '3');
        if (found && typeof found.client_id === 'string') {
          webClientId = found.client_id.trim();
          break;
        }
      }

      if (apiKey && projectId) {
        return {
          apiKey,
          authDomain: `${projectId}.firebaseapp.com`,
          projectId,
          storageBucket,
          messagingSenderId,
          appId,
          webClientId,
        };
      }
    }

    // 3. Direct JSON firebaseConfig object
    const direct = rawObj as Partial<FirebaseClientConfig>;
    if (direct.apiKey && direct.projectId) {
      return {
        apiKey: direct.apiKey.trim(),
        authDomain: (direct.authDomain || `${direct.projectId}.firebaseapp.com`).trim(),
        projectId: direct.projectId.trim(),
        storageBucket: direct.storageBucket?.trim(),
        messagingSenderId: direct.messagingSenderId?.trim(),
        appId: direct.appId?.trim(),
        webClientId: direct.webClientId?.trim(),
      };
    }
  } catch {
    // 4. Parse JS object snippet from Firebase Console (e.g. const firebaseConfig = { apiKey: "...", ... })
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
        webClientId: extract('webClientId'),
      };
    }
  }
  return null;
}

export async function saveRuntimeFirebaseConfig(config: FirebaseClientConfig | null): Promise<void> {
  if (typeof window === 'undefined') return;
  const nextHash = config ? `${config.projectId}:${config.apiKey}` : '';
  if (!config) {
    window.localStorage.removeItem(RUNTIME_CONFIG_STORAGE_KEY);
  } else {
    window.localStorage.setItem(RUNTIME_CONFIG_STORAGE_KEY, JSON.stringify(config));
  }
  if (appInstance && nextHash !== activeConfigHash) {
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
        const items = snap.docs
          .map((d) => d.data() as WishItem)
          .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
        handlers.onWishes(items);
      },
      (err) => handlers.onError?.(err)
    )
  );

  return () => {
    unsubs.forEach((u) => u());
  };
}
