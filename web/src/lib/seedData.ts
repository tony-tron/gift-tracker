import type { GiftDataset } from './types';

/**
 * Empty baseline dataset for clean installs before connecting Firebase.
 * All real user data lives strictly in the user's private Firebase Cloud Firestore.
 */
export const EMPTY_DATASET: GiftDataset = {
  people: [],
  events: [
    {
      id: '2026-christmas',
      name: '2026 Christmas',
      year: 2026,
      occasion: 'Christmas',
      emoji: '🎄',
      isActive: true,
      isArchived: false,
      order: 0,
    },
    {
      id: '2026-birthdays',
      name: '2026 Birthdays',
      year: 2026,
      occasion: 'Birthday',
      emoji: '🎂',
      isActive: true,
      isArchived: false,
      order: 1,
    },
  ],
  gifts: [],
  wishes: [],
  sharedLinks: [],
  savedWishlists: [],
};
