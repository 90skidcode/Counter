package com.shortvideotracker.core

import com.shortvideotracker.core.engine.AddressBarClassification.OtherSite
import com.shortvideotracker.core.engine.AddressBarClassification.ShortVideo
import com.shortvideotracker.core.engine.AddressBarClassification.SiteActivity
import com.shortvideotracker.core.engine.ShortVideoSite
import com.shortvideotracker.core.engine.UrlClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlClassifierTest {

    @Test
    fun youtubeShortsWithId() {
        assertEquals(
            ShortVideo("youtube.com", ShortVideoSite.YOUTUBE, "dQw4w9WgXcQ"),
            UrlClassifier.classify("https://m.youtube.com/shorts/dQw4w9WgXcQ?feature=share"),
        )
        assertEquals(
            ShortVideo("youtube.com", ShortVideoSite.YOUTUBE, "abcDEF12345"),
            UrlClassifier.classify("youtube.com/shorts/abcDEF12345"),
        )
    }

    @Test
    fun regularYoutubeIsSiteActivityOnly() {
        assertEquals(SiteActivity("youtube.com", ShortVideoSite.YOUTUBE), UrlClassifier.classify("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(SiteActivity("youtube.com", ShortVideoSite.YOUTUBE), UrlClassifier.classify("youtube.com"))
    }

    @Test
    fun instagramTiktokFacebook() {
        assertEquals(ShortVideoSite.INSTAGRAM, (UrlClassifier.classify("https://www.instagram.com/reel/C1a2B3c4D5e/") as ShortVideo).site)
        assertEquals("7312345678901234567", (UrlClassifier.classify("https://www.tiktok.com/@someone/video/7312345678901234567") as ShortVideo).videoId)
        assertEquals("123456789012", (UrlClassifier.classify("https://m.facebook.com/reel/123456789012") as ShortVideo).videoId)
    }

    @Test
    fun otherSitesAndSearchText() {
        assertEquals(OtherSite("example.org"), UrlClassifier.classify("https://example.org/shorts/abcdefgh"))
        assertNull(UrlClassifier.classify("how to cook rice"))
        assertNull(UrlClassifier.classify(""))
        assertNull(UrlClassifier.classify("localhost"))
    }

    @Test
    fun lookalikeDomainsDoNotMatch() {
        assertEquals(OtherSite("notyoutube.com"), UrlClassifier.classify("notyoutube.com/shorts/abcdefghij"))
        assertFalse(UrlClassifier.domainMatches("youtube.com.evil.example", "youtube.com"))
        assertTrue(UrlClassifier.domainMatches("music.youtube.com", "youtube.com"))
    }

    @Test
    fun domainInputNormalisation() {
        assertEquals("tiktok.com", UrlClassifier.normaliseDomainInput(" https://www.TikTok.com/foryou "))
        assertNull(UrlClassifier.normaliseDomainInput("not a domain"))
        assertNull(UrlClassifier.normaliseDomainInput("-bad-.com"))
        assertNull(UrlClassifier.normaliseDomainInput("nodot"))
    }
}
