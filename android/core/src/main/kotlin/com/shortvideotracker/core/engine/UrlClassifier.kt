package com.shortvideotracker.core.engine

/** Short-video websites recognised in a browser address bar. */
enum class ShortVideoSite(val code: String, val domains: List<String>) {
    YOUTUBE("youtube", listOf("youtube.com", "youtu.be")),
    INSTAGRAM("instagram", listOf("instagram.com")),
    TIKTOK("tiktok", listOf("tiktok.com")),
    FACEBOOK("facebook", listOf("facebook.com", "fb.watch")),
}

sealed interface AddressBarClassification {
    /** The host, normalised (lowercase, no `www.`/`m.`). Used for domain blocking. */
    val host: String

    /** A short-video page whose URL carries a stable video id. */
    data class ShortVideo(override val host: String, val site: ShortVideoSite, val videoId: String) :
        AddressBarClassification

    /** A known short-video site, but no individual video can be identified from the URL. */
    data class SiteActivity(override val host: String, val site: ShortVideoSite) : AddressBarClassification

    /** Any other website. Nothing about it is recorded. */
    data class OtherSite(override val host: String) : AddressBarClassification
}

/**
 * Classifies address-bar text in memory. The text itself is never stored or logged; callers
 * keep at most the [AddressBarClassification] result.
 */
object UrlClassifier {

    private val YOUTUBE_SHORTS = Regex("^/shorts/([A-Za-z0-9_-]{6,20})")
    private val INSTAGRAM_REEL = Regex("^/(?:reel|reels)/([A-Za-z0-9_-]{5,40})")
    private val TIKTOK_VIDEO = Regex("^/@[^/]+/video/(\\d{6,25})")
    private val FACEBOOK_REEL = Regex("^/reel/(\\d{5,25})")

    fun classify(addressBarText: String?): AddressBarClassification? {
        val text = addressBarText?.trim()?.takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
        val withoutScheme = text.substringAfter("://", text)
        val hostPart = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val rawHost = hostPart.substringBefore(':').lowercase()
        if (!rawHost.contains('.') || rawHost.startsWith('.')) return null
        val host = normaliseHost(rawHost)
        val path = withoutScheme.drop(hostPart.length).substringBefore('?').substringBefore('#')

        val site = ShortVideoSite.entries.firstOrNull { s -> s.domains.any { domainMatches(host, it) } }
            ?: return AddressBarClassification.OtherSite(host)

        val pattern = when (site) {
            ShortVideoSite.YOUTUBE -> YOUTUBE_SHORTS
            ShortVideoSite.INSTAGRAM -> INSTAGRAM_REEL
            ShortVideoSite.TIKTOK -> TIKTOK_VIDEO
            ShortVideoSite.FACEBOOK -> FACEBOOK_REEL
        }
        val id = pattern.find(path)?.groupValues?.get(1)
        return if (id != null) {
            AddressBarClassification.ShortVideo(host, site, id)
        } else {
            AddressBarClassification.SiteActivity(host, site)
        }
    }

    fun normaliseHost(host: String): String {
        var h = host.lowercase().trimEnd('.')
        for (prefix in listOf("www.", "m.", "mobile.")) {
            if (h.startsWith(prefix) && h.count { it == '.' } >= 2) h = h.removePrefix(prefix)
        }
        return h
    }

    /** True when [host] is [domain] or one of its subdomains. */
    fun domainMatches(host: String, domain: String): Boolean {
        val h = normaliseHost(host)
        val d = normaliseDomainInput(domain) ?: return false
        return h == d || h.endsWith(".$d")
    }

    /**
     * Validates a user-entered domain for the website block list. Returns the normalised
     * domain, or null if it is not a plausible domain name.
     */
    fun normaliseDomainInput(input: String): String? {
        val raw = input.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .trimEnd('.')
        if (raw.isEmpty() || raw.length > 253 || !raw.contains('.')) return null
        val labels = raw.split('.')
        val valid = labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.all { it.isLetterOrDigit() || it == '-' } &&
                !label.startsWith('-') && !label.endsWith('-')
        }
        return if (valid) normaliseHost(raw) else null
    }
}
