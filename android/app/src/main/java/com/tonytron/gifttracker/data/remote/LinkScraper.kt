package com.tonytron.gifttracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

data class ScrapedLinkResult(
    val url: String,
    val title: String,
    val store: String,
    val price: Double?
)

object LinkScraper {

    private val URL_EXTRACT_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    private val KNOWN_STORES: List<Pair<Regex, String>> = listOf(
        Regex("""amazon\.|a\.co\b""", RegexOption.IGNORE_CASE) to "Amazon",
        Regex("""target\.com""", RegexOption.IGNORE_CASE) to "Target",
        Regex("""etsy\.com""", RegexOption.IGNORE_CASE) to "Etsy",
        Regex("""cotopaxi\.com""", RegexOption.IGNORE_CASE) to "Cotopaxi",
        Regex("""hottopic\.com""", RegexOption.IGNORE_CASE) to "Hot Topic",
        Regex("""fangamer\.com""", RegexOption.IGNORE_CASE) to "Fangamer",
        Regex("""costco\.com""", RegexOption.IGNORE_CASE) to "Costco",
        Regex("""walmart\.com""", RegexOption.IGNORE_CASE) to "Walmart",
        Regex("""bestbuy\.com""", RegexOption.IGNORE_CASE) to "Best Buy",
        Regex("""barnesandnoble\.com""", RegexOption.IGNORE_CASE) to "Barnes & Noble",
        Regex("""store\.google\.com""", RegexOption.IGNORE_CASE) to "Google Store",
        Regex("""lululemon\.com""", RegexOption.IGNORE_CASE) to "Lululemon",
        Regex("""rei\.com""", RegexOption.IGNORE_CASE) to "REI",
        Regex("""displate\.com""", RegexOption.IGNORE_CASE) to "Displate",
        Regex("""dropout\.tv|store\.dropout""", RegexOption.IGNORE_CASE) to "Dropout",
        Regex("""nintendo\.com""", RegexOption.IGNORE_CASE) to "Nintendo Store",
        Regex("""chewy\.com""", RegexOption.IGNORE_CASE) to "Chewy",
        Regex("""redbubble\.com""", RegexOption.IGNORE_CASE) to "Redbubble",
        Regex("""patagonia\.com""", RegexOption.IGNORE_CASE) to "Patagonia",
        Regex("""gamestop\.com""", RegexOption.IGNORE_CASE) to "GameStop",
        Regex("""critrole\.com""", RegexOption.IGNORE_CASE) to "Critical Role"
    )

    /**
     * Extracts URL and fallback title from Android ACTION_SEND shared text
     * (e.g., "Cotopaxi Agua Bag https://www.cotopaxi.com/...").
     */
    fun parseSharedText(sharedText: String?, sharedSubject: String?): Pair<String, String> {
        val text = sharedText?.trim().orEmpty()
        val urlMatch = URL_EXTRACT_REGEX.find(text)?.value?.trimEnd('.', ',', ')').orEmpty()
        val leftoverText = text.replace(urlMatch, "").trim().trim('-', '|', ':').trim()
        val fallbackTitle = when {
            !sharedSubject.isNullOrBlank() && !sharedSubject.startsWith("http") -> sharedSubject.trim()
            leftoverText.isNotBlank() && leftoverText.length < 140 -> leftoverText
            else -> ""
        }
        return urlMatch to fallbackTitle
    }

    fun inferStoreFromUrl(rawUrl: String): String {
        for ((pattern, label) in KNOWN_STORES) {
            if (pattern.containsMatchIn(rawUrl)) return label
        }
        return runCatching {
            val host = URI(rawUrl).host?.removePrefix("www.") ?: return ""
            val base = host.substringBefore('.')
            base.replaceFirstChar { it.uppercase() }
        }.getOrDefault("")
    }

    suspend fun scrape(rawUrl: String): ScrapedLinkResult = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            return@withContext ScrapedLinkResult(trimmed, "", "", null)
        }

        val store = inferStoreFromUrl(trimmed)
        var resolvedUrl = trimmed
        var title = ""
        var price: Double? = null

        runCatching {
            val conn = (uri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                if (conn.responseCode in 200..299) {
                    resolvedUrl = conn.url?.toString() ?: trimmed
                    val html = conn.inputStream.bufferedReader().use { reader ->
                        val buf = CharArray(120_000)
                        val count = reader.read(buf)
                        if (count > 0) String(buf, 0, count) else ""
                    }

                    val ogTitle = Regex(
                        """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(html)?.groupValues?.getOrNull(1)
                        ?: Regex(
                            """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']""",
                            RegexOption.IGNORE_CASE
                        ).find(html)?.groupValues?.getOrNull(1)
                        ?: Regex(
                            """<title[^>]*>([^<]+)</title>""",
                            RegexOption.IGNORE_CASE
                        ).find(html)?.groupValues?.getOrNull(1)

                    if (!ogTitle.isNullOrBlank()) {
                        title = cleanProductTitle(ogTitle, store)
                    }

                    val priceRaw = Regex(
                        """<meta[^>]+property=["'](?:product:price:amount|og:price:amount)["'][^>]+content=["']([0-9.]+)["']""",
                        RegexOption.IGNORE_CASE
                    ).find(html)?.groupValues?.getOrNull(1)
                        ?: Regex(
                            """"price"\s*:\s*"?([0-9]+\.[0-9]{2})"?""",
                            RegexOption.IGNORE_CASE
                        ).find(html)?.groupValues?.getOrNull(1)

                    val parsedPrice = priceRaw?.toDoubleOrNull()
                    if (parsedPrice != null && parsedPrice > 0.0 && parsedPrice < 50_000.0) {
                        price = parsedPrice
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

        val finalStore = inferStoreFromUrl(resolvedUrl).ifBlank { store }

        if (title.isBlank()) {
            runCatching {
                val path = URI(resolvedUrl).path.orEmpty()
                val slug = path.split('/')
                    .filter { it.isNotBlank() && it != "dp" && it != "gp" && !it.matches(Regex("^[A-Z0-9]{10}$")) }
                    .lastOrNull()
                    .orEmpty()
                if (slug.length > 2) {
                    title = URLDecoder.decode(slug, "UTF-8")
                        .replace(Regex("[-_]+"), " ")
                        .split(" ")
                        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                        .take(80)
                }
            }
        }

        ScrapedLinkResult(
            url = resolvedUrl,
            title = title,
            store = finalStore,
            price = price
        )
    }

    private fun cleanProductTitle(raw: String, store: String): String {
        var t = raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""\s+"""), " ")
            .trim()
        t = t.replace(Regex("""^Amazon\.com\s*:\s*""", RegexOption.IGNORE_CASE), "")
        if (store.isNotBlank()) {
            t = t.replace(Regex("""\s*[-|•:]\s*${Regex.escape(store)}.*$""", RegexOption.IGNORE_CASE), "")
        }
        t = t.replace(Regex("""\s*[-|]\s*(Amazon\.com|Target|Etsy|Walmart\.com).*$""", RegexOption.IGNORE_CASE), "")
        return t.trim()
    }
}
