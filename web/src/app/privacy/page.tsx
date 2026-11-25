import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = {
  title: 'Privacy Policy — Gift Tracker',
  description:
    'Privacy Policy for the Gift Tracker Android app and Progressive Web App.',
};

const LAST_UPDATED = 'November 25, 2026';
const CONTACT_EMAIL = 'twintitaniumteam@gmail.com';

export default function PrivacyPolicyPage() {
  return (
    <main
      style={{
        minHeight: '100dvh',
        background: 'var(--md-bg, #16110f)',
        color: 'var(--md-on-surface, #f5e6e0)',
        padding: '32px 20px 64px',
        fontFamily: 'var(--font-sans, system-ui, sans-serif)',
      }}
    >
      <div
        style={{
          maxWidth: '760px',
          margin: '0 auto',
          background: 'var(--md-surface-container, #221a17)',
          border: '1px solid var(--md-outline-variant, rgba(255,255,255,0.1))',
          borderRadius: '24px',
          padding: '32px 28px',
          lineHeight: 1.65,
        }}
      >
        <div style={{ marginBottom: '24px' }}>
          <Link
            href="/"
            style={{
              color: 'var(--md-primary, #ffb59b)',
              textDecoration: 'none',
              fontSize: '14px',
              fontWeight: 600,
            }}
          >
            ← Back to Gift Tracker
          </Link>
        </div>

        <h1
          style={{
            fontFamily: 'var(--font-display, Georgia, serif)',
            fontSize: '32px',
            lineHeight: 1.2,
            margin: '0 0 8px',
          }}
        >
          Privacy Policy
        </h1>
        <p
          style={{
            margin: '0 0 28px',
            fontSize: '14px',
            opacity: 0.75,
          }}
        >
          Gift Tracker (Android &amp; Web) · Last updated: {LAST_UPDATED}
        </p>

        <Section title="1. Overview">
          <p>
            Gift Tracker (&ldquo;we&rdquo;, &ldquo;our&rdquo;, or &ldquo;the app&rdquo;) is a
            privacy-first personal gift planning and wishlist application available on Android
            (<code>com.twintitanium.gifttracker</code>) and the web. We do not sell your personal
            data, we do not display third-party advertisements, and we do not include third-party
            analytics or ad-tracking SDKs.
          </p>
        </Section>

        <Section title="2. Data Storage &amp; What We Collect">
          <ul>
            <li>
              <strong>Local Device Storage (Default):</strong> By default, all people, events,
              gift ideas, budgets, and wishlists you create are stored locally on your device
              (using Android DataStore on Android and browser storage on the web). This data never
              leaves your device unless you enable Cloud Sync or share a wishlist link.
            </li>
            <li>
              <strong>Optional Cloud Sync &amp; Google Sign-In:</strong> If you choose to enable
              Firebase Cloud Sync, we use Google Sign-In (via Android Credential Manager / Firebase
              Authentication) to authenticate your account. Firebase Authentication processes your
              Google Account user ID (UID), email address, and display name solely to isolate and
              sync your personal workspace (<code>workspaces/&#123;uid&#125;</code>) in Google
              Cloud Firestore across your devices.
            </li>
            <li>
              <strong>Collaborative Shared Wishlists:</strong> When you generate an unlisted
              wishlist link (<code>/w/&#123;shareId&#125;</code>), the items in that wishlist are
              stored in Cloud Firestore so anyone with the link can view them. Friends who claim an
              item may enter a display name and optional note, which are stored in Firestore so
              other gift-givers can coordinate without spoiling the surprise.
            </li>
            <li>
              <strong>Product Link Metadata Fetching:</strong> When you paste or share a product
              URL into Gift Tracker, the app may fetch the public webpage directly from your device
              to extract the product title, store name, or price.
            </li>
          </ul>
        </Section>

        <Section title="3. How We Use Information">
          <p>Information processed by Gift Tracker is used strictly to:</p>
          <ul>
            <li>Save and display your gift lists, recipients, occasions, and budgets.</li>
            <li>Synchronize your workspace across your own devices when Cloud Sync is enabled.</li>
            <li>Enable real-time collaborative claiming on unlisted wishlists you choose to share.</li>
          </ul>
        </Section>

        <Section title="4. Third-Party Services">
          <p>
            When Cloud Sync or Shared Wishlists are used, data is stored and transmitted using{' '}
            <strong>Google Firebase</strong> (Firebase Authentication and Cloud Firestore),
            governed by Google&apos;s Privacy Policy. Data is encrypted in transit via HTTPS/TLS.
          </p>
        </Section>

        <Section title="5. Data Retention, Export &amp; Deletion">
          <ul>
            <li>
              <strong>Local Data:</strong> You can export a full JSON backup at any time in
              Settings, or clear all local data by uninstalling the app or clearing app/browser
              storage.
            </li>
            <li>
              <strong>Cloud Data &amp; Account Deletion:</strong> You can delete individual gifts,
              people, events, or shared wishlist links directly inside the app, or disconnect Cloud
              Sync in Settings. To request complete deletion of your Cloud Firestore workspace and
              authentication record, contact us at{' '}
              <a
                href={`mailto:${CONTACT_EMAIL}?subject=Gift%20Tracker%20Data%20Deletion%20Request`}
                style={{ color: 'var(--md-primary, #ffb59b)' }}
              >
                {CONTACT_EMAIL}
              </a>
              .
            </li>
          </ul>
        </Section>

        <Section title="6. Children&apos;s Privacy">
          <p>
            Gift Tracker is not directed to children under the age of 13, and we do not knowingly
            collect personal information from children under 13.
          </p>
        </Section>

        <Section title="7. Changes &amp; Contact">
          <p>
            We may update this Privacy Policy from time to time. Any updates will be posted on
            this page with an updated &ldquo;Last updated&rdquo; date. If you have questions about
            this Privacy Policy or your data, please contact us at{' '}
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              style={{ color: 'var(--md-primary, #ffb59b)' }}
            >
              {CONTACT_EMAIL}
            </a>
            .
          </p>
        </Section>
      </div>
    </main>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ marginBottom: '24px' }}>
      <h2
        style={{
          fontSize: '18px',
          fontWeight: 700,
          margin: '0 0 8px',
          color: 'var(--md-primary, #ffb59b)',
        }}
      >
        {title}
      </h2>
      <div style={{ fontSize: '15px', opacity: 0.92 }}>{children}</div>
    </section>
  );
}
