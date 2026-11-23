export type AvatarShape = 'squircle' | 'cookie' | 'clover' | 'arch' | 'pill' | 'flower';

export type AvatarTone = 'terracotta' | 'sage' | 'amber' | 'teal' | 'rose' | 'ochre';

export type ThemeSeed = 'terracotta' | 'forest' | 'ochre' | 'ocean';

export type ColorMode = 'dark' | 'light' | 'system';

export type OccasionType = 'Christmas' | 'Birthday' | 'Anniversary' | 'Other';

export interface ClothingSizes {
  top?: string;
  bottoms?: string;
  intimates?: string;
  shoe?: string;
  ring?: string;
  other?: string;
}

export interface Person {
  id: string;
  name: string;
  budget: number;
  relationship: string;
  sizes: ClothingSizes;
  notes: string;
  avatarShape: AvatarShape;
  avatarTone: AvatarTone;
  birthday?: string;
  order: number;
}

export interface GiftEvent {
  id: string;
  name: string;
  year: number;
  occasion: OccasionType;
  emoji: string;
  isActive: boolean;
  isArchived: boolean;
  order: number;
}

export interface GiftItem {
  id: string;
  title: string;
  personId: string | null; // null = "Not sure who" general idea pool
  eventId: string | null;  // null = Ideas Backlog / Stockpile
  cost: number;
  costLabel?: string | null; // e.g., "$30–$200" range from sheet
  store: string;
  url: string;
  tracking: string;
  notes: string;
  bought: boolean;
  received: boolean;
  wrapped: boolean;
  saveForLater: boolean;
  createdAt: string;
}

export interface WishItem {
  id: string;
  title: string;
  url: string;
  notes: string;
  received: boolean;
  receivedFrom: string;
  receivedWhen: string;
  priority: 'high' | 'medium' | 'low';
  order?: number;
}

export interface GiftDataset {
  people: Person[];
  events: GiftEvent[];
  gifts: GiftItem[];
  wishes: WishItem[];
}

export interface FirebaseClientConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket?: string;
  messagingSenderId?: string;
  appId?: string;
}

export type NavTab = 'events' | 'ideas' | 'people' | 'wishlist';
