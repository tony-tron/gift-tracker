import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Gift Tracker',
    short_name: 'Gifts',
    description:
      'Material 3 Expressive Gift Tracker synced across Android phones, foldables, and Googlebook.',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    orientation: 'any',
    background_color: '#171210',
    theme_color: '#171210',
    icons: [
      {
        src: '/icons/icon-192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
    share_target: {
      action: '/share-target',
      method: 'GET',
      enctype: 'application/x-www-form-urlencoded',
      params: {
        title: 'title',
        text: 'text',
        url: 'url',
      },
    },
    shortcuts: [
      {
        name: 'Add Gift Idea',
        short_name: 'New Idea',
        description: 'Quickly jot down a gift idea',
        url: '/?action=new-idea',
        icons: [{ src: '/icons/icon-192.png', sizes: '192x192' }],
      },
      {
        name: '2026 Christmas',
        short_name: 'Christmas',
        description: 'Open active 2026 Christmas checklist',
        url: '/?tab=events&event=2026-christmas',
        icons: [{ src: '/icons/icon-192.png', sizes: '192x192' }],
      },
      {
        name: 'Sizes Cheat Sheet',
        short_name: 'Sizes',
        description: 'Check clothing sizes & notes by person',
        url: '/?tab=people',
        icons: [{ src: '/icons/icon-192.png', sizes: '192x192' }],
      },
    ],
  };
}
