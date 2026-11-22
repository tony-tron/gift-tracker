import { NextRequest, NextResponse } from 'next/server';

const KNOWN_STORES: Array<[RegExp, string]> = [
  [/amazon\.|a\.co\b/i, 'Amazon'],
  [/target\.com/i, 'Target'],
  [/etsy\.com/i, 'Etsy'],
  [/cotopaxi\.com/i, 'Cotopaxi'],
  [/hottopic\.com/i, 'Hot Topic'],
  [/fangamer\.com/i, 'Fangamer'],
  [/costco\.com/i, 'Costco'],
  [/walmart\.com/i, 'Walmart'],
  [/bestbuy\.com/i, 'Best Buy'],
  [/barnesandnoble\.com/i, 'Barnes & Noble'],
  [/store\.google\.com/i, 'Google Store'],
  [/lululemon\.com/i, 'Lululemon'],
  [/rei\.com/i, 'REI'],
  [/displate\.com/i, 'Displate'],
  [/dropout\.tv|store\.dropout/i, 'Dropout'],
  [/nintendo\.com/i, 'Nintendo Store'],
  [/chewy\.com/i, 'Chewy'],
  [/redbubble\.com/i, 'Redbubble'],
  [/patagonia\.com/i, 'Patagonia'],
  [/gamestop\.com/i, 'GameStop'],
  [/critrole\.com/i, 'Critical Role'],
];

function inferStoreFromUrl(rawUrl: string): string {
  for (const [pattern, label] of KNOWN_STORES) {
    if (pattern.test(rawUrl)) return label;
  }
  try {
    const host = new URL(rawUrl).hostname.replace(/^www\./i, '');
    const base = host.split('.')[0] || '';
    return base ? base.charAt(0).toUpperCase() + base.slice(1) : '';
  } catch {
    return '';
  }
}

function decodeHtmlEntities(str: string): string {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim();
}

function cleanProductTitle(raw: string, store: string): string {
  let t = decodeHtmlEntities(raw);
  t = t.replace(/^Amazon\.com\s*:\s*/i, '');
  if (store) {
    const suffixRe = new RegExp(`\\s*[-|•:]\\s*${store}.*$`, 'i');
    t = t.replace(suffixRe, '');
  }
  t = t.replace(/\s*[-|]\s*(Amazon\.com|Target|Etsy|Walmart\.com).*$/i, '');
  return t.trim();
}

export async function GET(req: NextRequest) {
  const rawUrl = req.nextUrl.searchParams.get('url')?.trim() || '';
  if (!rawUrl) {
    return NextResponse.json({ url: '', title: '', store: '', price: null });
  }

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(rawUrl);
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      return NextResponse.json({ url: rawUrl, title: '', store: '', price: null });
    }
  } catch {
    return NextResponse.json({ url: rawUrl, title: '', store: '', price: null });
  }

  const store = inferStoreFromUrl(parsedUrl.toString());
  let title = '';
  let price: number | null = null;

  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 3200);
    const res = await fetch(parsedUrl.toString(), {
      signal: controller.signal,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Linux; Android 16; Pixel 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36',
        Accept: 'text/html,application/xhtml+xml',
      },
    });
    clearTimeout(timer);

    if (res.ok) {
      const html = (await res.text()).slice(0, 120_000);

      const ogTitle =
        html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i)?.[1] ||
        html.match(/<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']/i)?.[1] ||
        html.match(/<title[^>]*>([^<]+)<\/title>/i)?.[1] ||
        '';
      if (ogTitle) {
        title = cleanProductTitle(ogTitle, store);
      }

      const priceRaw =
        html.match(
          /<meta[^>]+property=["'](?:product:price:amount|og:price:amount)["'][^>]+content=["']([0-9.]+)["']/i
        )?.[1] ||
        html.match(/"price"\s*:\s*"?([0-9]+\.[0-9]{2})"?/i)?.[1];
      if (priceRaw) {
        const parsed = parseFloat(priceRaw);
        if (!Number.isNaN(parsed) && parsed > 0 && parsed < 50000) {
          price = parsed;
        }
      }
    }
  } catch {
    // Graceful fallback when blocked or offline — inferred store & URL path still work!
  }

  if (!title) {
    // Extract readable slug from URL pathname if title couldn't be scraped
    const segments = parsedUrl.pathname
      .split('/')
      .filter((s) => s && s !== 'dp' && s !== 'gp' && !/^[A-Z0-9]{10}$/.test(s));
    const slug = segments[segments.length - 1] || '';
    if (slug && slug.length > 2) {
      title = decodeURIComponent(slug)
        .replace(/[-_]+/g, ' ')
        .replace(/\b\w/g, (c) => c.toUpperCase())
        .slice(0, 80);
    }
  }

  return NextResponse.json({
    url: parsedUrl.toString(),
    title,
    store,
    price,
  });
}
