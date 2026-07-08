package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import android.util.Base64
import org.json.JSONObject
import java.net.URL

class KrmziProvider : MainAPI() {
    override var mainUrl = "https://krmzi.org"
    override var name = "قرمزي (Krmzi)"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "ar"
    override val hasMainPage = true

    // 🚀 جلب القوائم الرئيسية (أفلام / مسلسلات)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        
        // جلب قائمة المسلسلات
        val seriesHtml = app.get("$mainUrl/series-list/page/$page/").text
        val series = parsePage(seriesHtml)
        if (series.isNotEmpty()) items.add(HomePageList("مسلسلات", series))

        // جلب قائمة الأفلام
        val moviesHtml = app.get("$mainUrl/movies-list/page/$page/").text
        val movies = parsePage(moviesHtml)
        if (movies.isNotEmpty()) items.add(HomePageList("أفلام", movies))

        return HomePageResponse(items, hasNext = true)
    }

    private fun parsePage(html: String): List<SearchResponse> {
        val document = org.jsoup.Jsoup.parse(html)
        val list = ArrayList<SearchResponse>()
        val seen = HashSet<String>()

        document.select("article, .block-post, .col-xs-5th").forEach { el ->
            val a = el.select("a").first()
            val link = a?.attr("href")
            if (link != null && !seen.contains(link)) {
                val style = el.select(".imgBg, .imgSer, .posterThumb div").attr("style") ?: ""
                var thumb = Regex("""url\((.*?)\)""").find(style)?.groupValues?.get(1)?.replace("'", "")?.replace("\"", "")
                if (thumb.isNullOrEmpty()) {
                    thumb = el.select("img").attr("data-src").ifEmpty { el.select("img").attr("src") }
                }

                val title = (a.attr("title").ifEmpty { el.select(".title").text() }).replace("- قرمزي", "").trim()

                list.add(newMovieSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = thumb
                })
                seen.add(link)
            }
        }
        return list
    }

    // 🚀 محرك البحث
    override suspend fun search(query: String): List<SearchResponse> {
        val html = app.get("$mainUrl/?s=${valEnc(query)}").text
        val document = org.jsoup.Jsoup.parse(html)
        val results = ArrayList<SearchResponse>()
        val seen = HashSet<String>()

        document.select(".result-item, article, .block-post").forEach { el ->
            val a = el.select("a").first()
            val link = a?.attr("href")
            if (link != null && !seen.contains(link)) {
                val style = el.select(".imgBg, .imgSer").attr("style") ?: ""
                var thumb = Regex("""url\((.*?)\)""").find(style)?.groupValues?.get(1)?.replace("'", "")?.replace("\"", "")
                if (thumb.isNullOrEmpty()) {
                    thumb = el.select("img").attr("data-src").ifEmpty { el.select("img").attr("src") }
                }

                val title = a.attr("title").ifEmpty { el.select(".title, h2").text().trim() }

                results.add(newMovieSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = thumb
                })
                seen.add(link)
            }
        }
        return results
    }

    // 🚀 جلب تفاصيل الفيلم أو المسلسل وقائمة الحلقات
    override suspend fun load(url: String): LoadResponse? {
        val isEp = url.contains("/episode/")
        val html = app.get(url).text
        val document = org.jsoup.Jsoup.parse(html)
        val title = document.select("h1").first()?.text()?.trim() ?: ""
        val poster = document.select(".poster img").attr("src")

        if (!isEp) {
            val episodes = ArrayList<Episode>()
            val seenEp = HashSet<String>()
            document.select(".postEp, .episodes-list a").forEach { el ->
                val a = if (el.tagName() == "a") el else el.select("a").first()
                val epUrl = a?.attr("href")
                if (epUrl != null && epUrl.contains("/episode/") && !seenEp.contains(epUrl)) {
                    episodes.add(Episode(epUrl, a.text().trim().ifEmpty { "حلقة" }))
                    seenEp.add(epUrl)
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
                this.posterUrl = poster
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
            }
        }
    }

    // 🚀 جلب السيرفرات (الموزع الذكي والأكواد وفك تشفير الجسر)
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val html = app.get(data).text
        val document = org.jsoup.Jsoup.parse(html)

        // 1️⃣ فحص قائمة data-server الجانبية
        document.select("li[data-server]").forEach { el ->
            val sId = el.attr("data-server")
            val sNameAttr = el.attr("data-name")
            val sTitle = el.select("span").text().trim().ifEmpty { "مشغل" }

            if (!sId.isNullOrEmpty() && sId != "undefined") {
                val finalUrl = when (sNameAttr) {
                    "estream" -> "https://arabveturk.com/embed-$sId.html"
                    "iplayer" -> "https://iplayerhls.com/e/$sId"
                    "ok" -> "https://ok.ru/videoembed/$sId"
                    "mailru" -> "https://my.mail.ru/video/embed/$sId"
                    else -> if (sId.startsWith("http")) sId else "https://arabhd.onl/embed-$sId.html"
                }
                loadExtractor(finalUrl, sTitle, callback)
            }
        }

        // 2️⃣ فك تشفير نظام الجسر (Base64 Bridge)
        val bridge = document.select("a[href*='post='], iframe[src*='post=']").map { it.attr("href").ifEmpty { it.attr("src") } }.firstOrNull()
        if (bridge != null) {
            try {
                val uri = URL(bridge)
                val queryMap = uri.query.split("&").associate { val (k, v) = it.split("="); k to v }
                val postParam = queryMap["post"]
                if (postParam != null) {
                    val decodedBytes = Base64.decode(postParam, Base64.DEFAULT)
                    val decodedString = String(decodedBytes, Charsets.UTF_8)
                    val json = JSONObject(decodedString)
                    val serversArray = json.optJSONArray("servers")
                    
                    if (serversArray != null) {
                        for (i in 0 until serversArray.length()) {
                            val srv = serversArray.getJSONObject(i)
                            val sName = srv.getString("name").lowercase()
                            val sId = srv.getString("id")
                            
                            val fUrl = when {
                                sName.contains("ok") -> "https://ok.ru/videoembed/$sId"
                                sName.contains("pro hd") || sName.contains("larhu") -> "https://w.larhu.website/play.php?id=$sId"
                                sName.contains("red hd") || sName.contains("iplayer") -> "https://iplayerhls.com/e/$sId"
                                sName.contains("estream") || sName.contains("turk") -> "https://arabveturk.com/embed-$sId.html"
                                else -> if (sId.startsWith("http")) sId else "https://arabhd.onl/embed-$sId.html"
                            }
                            loadExtractor(fUrl, srv.getString("name"), callback)
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // 3️⃣ صيد الـ iFrames المباشرة
        document.select(".getEmbed, .watch").select("iframe").forEach { el ->
            var src = el.attr("src")
            if (!src.isNullOrEmpty() && !src.contains("ads")) {
                if (src.startsWith("//")) src = "https:$src"
                val name = if (src.contains("larhu")) "Larhu Website" else "سيرفر مباشر"
                loadExtractor(src, name, callback)
            }
        }

        return true
    }

    // دالة مساعدة لتشغيل الـ Extractors المدمجة في كلاود ستريم تلقائياً
    private suspend fun loadExtractor(url: String, name: String, callback: (ExtractorLink) -> Unit) {
        // يقوم التطبيق تلقائياً بفحص الرابط وتشغيله إذا كان مدعوماً (مثل Ok.ru أو iPlayer)
        loadExtractor(url, referer = mainUrl, name = name, callback = callback)
    }

    private fun valEnc(q: String): String = java.net.URLEncoder.encode(q, "UTF-8")
}
