package com.example

import com.example.service.IdentityService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testAsocksFetch() = runBlocking {
    val newUrl = "https://asocks-list.org/nNXGLqOxzG66RnkaXmZCZ7VoXTuuWUw8.txt?limit=10&type=res&template_id=2&country=US"
    println("Testing new Asocks URL: $newUrl")
    val res = IdentityService.fetchProxiesFromUrl(newUrl)
    println("Fetch result: isSuccess=${res.isSuccess}")
    if (res.isFailure) {
      println("Live external network unavailable or expired: ${res.exceptionOrNull()?.message}")
      return@runBlocking
    }
    val proxies = res.getOrNull() ?: return@runBlocking
    println("Parsed proxies count: ${proxies.size}")
    for (p in proxies) {
      println("Proxy parsed: ${p.host}:${p.port} [${p.type}] user='${p.username}' pass='${p.password}'")
    }

    var workingCount = 0
    var failedCount = 0
    for ((index, p) in proxies.withIndex()) {
      val diag = IdentityService.testAndDetectProxy(p.host, p.port, p.type, p.username, p.password, 12000)
      println("Proxy #$index [${p.host}:${p.port}]: working=${diag.isWorking}, exitIp=${diag.exitIp}, ping=${diag.pingMs}ms, error=${diag.errorMessage}")
      if (diag.isWorking) workingCount++ else failedCount++
    }
    println("Summary: working=$workingCount, failed=$failedCount out of ${proxies.size}")
  }

  @Test
  fun testGdfqoFullFunnelInspection() = runBlocking {
    val sb = StringBuilder()
    fun log(msg: String) {
      println(msg)
      sb.appendLine(msg)
    }
    val client = okhttp3.OkHttpClient.Builder()
      .followRedirects(true)
      .followSslRedirects(true)
      .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
      .build()
    val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"

    log("=== STEP 1: FETCHING GDFQO.BLOGSPOT.COM ===")
    try {
      val req1 = okhttp3.Request.Builder()
        .url("https://gdfqo.blogspot.com/?utm_source=facebook&utm_medium=cpc&utm_campaign=tools&utm_content=tools_ad_1")
        .header("User-Agent", ua)
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Referer", "https://www.facebook.com/")
        .build()
      val resp1 = client.newCall(req1).execute()
      val body1 = resp1.body?.string().orEmpty()
      log("GDFQO HTTP ${resp1.code}, finalUrl=${resp1.request.url}, len=${body1.length}")
      val scripts = Regex("""<script[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).findAll(body1).map { it.groupValues[1] }.toList()
      log("GDFQO Script SRCs: $scripts")
      val inlineScripts = Regex("""<script[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE).findAll(body1).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
      for ((idx, s) in inlineScripts.withIndex()) {
        if (s.contains("locker", true) || s.contains("1741238") || s.contains("alignment") || s.contains("cpagrip") || s.contains("call_locker") || s.contains("button") || s.length < 2500) {
          log("GDFQO Inline Script #$idx:\n${s.take(1500)}")
        }
      }
      val buttonsAndLinks = Regex("""<(?:button|a|div)\b[^>]*(?:onclick|href|class|id)[^>]*>([\s\S]*?)</(?:button|a|div)>""", RegexOption.IGNORE_CASE).findAll(body1).map { it.value.replace(Regex("\\s+"), " ").take(260) }.toList()
      log("GDFQO Interactive Elements (${buttonsAndLinks.size}):")
      buttonsAndLinks.take(35).forEach { log("  -> $it") }

      // Step 2: Fetch Locker Script
      val rawLockerUrl = scripts.firstOrNull { it.contains("script_include") || it.contains("alignment") || it.contains("cpagrip") }
        ?: "https://alignmentfiles.com/script_include.php?id=1741238"
      val lockerScriptUrl = if (rawLockerUrl.startsWith("//")) "https:$rawLockerUrl" else rawLockerUrl
      log("=== STEP 2: FETCHING LOCKER SCRIPT: $lockerScriptUrl ===")
      val req2 = okhttp3.Request.Builder()
        .url(lockerScriptUrl)
        .header("User-Agent", ua)
        .header("Referer", "https://gdfqo.blogspot.com/")
        .build()
      val resp2 = client.newCall(req2).execute()
      val body2 = resp2.body?.string().orEmpty()
      log("LOCKER SCRIPT HTTP ${resp2.code}, len=${body2.length}")
      log("LOCKER SCRIPT FULL CONTENT:\n$body2")

      // Replicate hex_encode from script_include.php to fetch preload.php with live offers!
      fun hexEncode(str: String): String {
        val min = 5
        val max = 126
        val range = (max - min) + 1
        val move = range / 2
        val sbHex = StringBuilder()
        for (ch in str) {
          var v = ch.code - min + move
          if (v >= range) v -= range
          val hex = v.toString(16).padStart(2, '0')
          sbHex.append(hex)
        }
        return sbHex.toString()
      }

      val aVal = Regex("""var\s+a\s*=\s*(\d+);""").find(body2)?.groupValues?.get(1) ?: "712"
      val bVal = Regex("""var\s+b\s*=\s*(\d+);""").find(body2)?.groupValues?.get(1) ?: "1741950"
      val cVal = Regex("""var\s+c\s*=\s*(\d+);""").find(body2)?.groupValues?.get(1) ?: "302"
      val dVal = Regex("""var\s+d\s*=\s*(\d+);""").find(body2)?.groupValues?.get(1) ?: "31"
      val eVal = Regex("""var\s+e\s*=\s*(\d+);""").find(body2)?.groupValues?.get(1) ?: "2228244"
      val fVal = Regex("""var\s+f\s*=\s*(\d+);""").find(body2)?.groupValues?.get(1) ?: "1014"
      val gVal = Regex("""var\s+g\s*=\s*'([^']*)';""").find(body2)?.groupValues?.get(1) ?: "a"
      val hVal = Regex("""var\s+h\s*=\s*'([^']*)';""").find(body2)?.groupValues?.get(1) ?: ""
      val pVal = Regex("""var\s+p\s*=\s*'([^']*)';""").find(body2)?.groupValues?.get(1) ?: ""
      val tParam = Regex("""preload\.php\?a=1&t=(\d+)""").find(body2)?.groupValues?.get(1) ?: "1791275793"

      val docRef = "https://www.facebook.com/"
      val encodedPayload = pVal + hexEncode("$aVal\t$bVal\t$cVal\t$dVal\t$eVal\t$fVal\t$gVal\t$docRef") + hVal + hexEncode("\t200")
      val fullPreloadUrl = "https://alignmentfiles.com/common/preload.php?a=1&t=$tParam&lkt=1&dat=$encodedPayload"
      log("=== STEP 2B: FETCHING LIVE PRELOAD OFFERS: $fullPreloadUrl ===")
      val preloadReq = okhttp3.Request.Builder()
        .url(fullPreloadUrl)
        .header("User-Agent", ua)
        .header("Referer", "https://gdfqo.blogspot.com/")
        .build()
      val preloadResp = client.newCall(preloadReq).execute()
      val preloadBody = preloadResp.body?.string().orEmpty()
      log("PRELOAD HTTP ${preloadResp.code}, len=${preloadBody.length}")
      log("PRELOAD FULL CONTENT:\n$preloadBody")

      // Also fetch mobile_template.php full content
      val mobileTemplateUrl = "https://alignmentfiles.com/mobile_template.php?a=1&i=0&lkt=1&id=1741238&tracking_id=&url=https%3A%2F%2Fgdfqo.blogspot.com%2F"
      val mtResp = client.newCall(
        okhttp3.Request.Builder()
          .url(mobileTemplateUrl)
          .header("User-Agent", ua)
          .header("Referer", "https://gdfqo.blogspot.com/")
          .build()
      ).execute()
      val mtBody = mtResp.body?.string().orEmpty()
      log("=== STEP 2C: MOBILE_TEMPLATE.PHP FULL CONTENT (len=${mtBody.length}) ===\n$mtBody")

      val unescapedPreload = preloadBody.replace("\\/", "/").replace("\\\"", "\"").replace("\\'", "'")
      val preloadOfferUrls = Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
        .findAll(unescapedPreload)
        .map { it.value.trimEnd(';', ')', ',', '\'', '"', '}') }
        .distinct()
        .toList()
      log("EXTRACTED OFFER URLs FROM PRELOAD (${preloadOfferUrls.size}):")
      preloadOfferUrls.forEach { log("  [OFFER_URL] $it") }

      // Step 3: Follow ALL offer tracking URLs to see the destination site(s)!
      val trackingUrls = preloadOfferUrls.filter {
        it.contains("show.php") || it.contains("click.php") || it.contains("tracking") ||
        (!it.endsWith(".png") && !it.endsWith(".jpg") && !it.endsWith(".gif") && !it.endsWith(".css"))
      }.take(8)

      for ((tIdx, trackingUrl) in trackingUrls.withIndex()) {
        log("\n=== STEP 3.${tIdx + 1}: FOLLOWING OFFER TRACKING URL: $trackingUrl ===")
        val cookieJar = object : okhttp3.CookieJar {
          private val store = mutableListOf<okhttp3.Cookie>()
          override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            store.addAll(cookies)
          }
          override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = store
        }
        val noRedirectClient = client.newBuilder()
          .cookieJar(cookieJar)
          .followRedirects(false)
          .followSslRedirects(false)
          .build()
        var currentUrl = trackingUrl
        var prevUrl = "https://alignmentfiles.com/mobile_template.php?a=1&i=0&lkt=1&id=1741238&tracking_id=&url=https%3A%2F%2Fgdfqo.blogspot.com%2F"
        var hop = 0
        var lastBody = ""
        while (hop < 10) {
          hop++
          val hopReq = okhttp3.Request.Builder()
            .url(currentUrl)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-CH-UA", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            .header("Sec-CH-UA-Mobile", "?1")
            .header("Sec-CH-UA-Platform", "\"Android\"")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "cross-site")
            .header("Sec-Fetch-User", "?1")
            .header("Referer", prevUrl)
            .build()
          val hopResp = noRedirectClient.newCall(hopReq).execute()
          val loc = hopResp.header("Location") ?: hopResp.header("Refresh")?.substringAfter("url=", "")?.takeIf { it.isNotBlank() }
          lastBody = hopResp.body?.string().orEmpty()
          val headersSummary = hopResp.headers.toMultimap().entries.joinToString("; ") { "${it.key}=${it.value}" }
          log("  Hop #$hop: HTTP ${hopResp.code} | url=$currentUrl | Location=$loc | bodyLen=${lastBody.length}")
          log("  Hop #$hop Headers: $headersSummary")
          if (loc != null) {
            prevUrl = currentUrl
            currentUrl = try { java.net.URI(currentUrl).resolve(loc).toString() } catch (_: Exception) { loc }
          } else {
            log("  Final Hop #$hop Body (first 4000 chars):\n${lastBody.take(4000)}")
            if (lastBody.length > 4000 && !currentUrl.contains("google.com")) {
              val destFile = java.io.File("build/destination_offer_${tIdx + 1}.html")
              destFile.parentFile?.mkdirs()
              destFile.writeText("<!-- URL: $currentUrl -->\n$lastBody")
              log("  [SAVED FULL DESTINATION HTML (${lastBody.length} chars) to ${destFile.path}]")
            }
            val jsRedirects = Regex("""(?:location\.href|location\.replace|window\.location(?:\.href)?)\s*[=(]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
              .findAll(lastBody).map { it.groupValues[1] }.toList()
            val metaRefresh = Regex("""content=["'][^"']*url=([^"'\s;>]+)""", RegexOption.IGNORE_CASE)
              .findAll(lastBody).map { it.groupValues[1] }.toList()
            val formActions = Regex("""<form[^>]*action=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
              .findAll(lastBody).map { it.groupValues[1] }.toList()
            val inputs = Regex("""<(?:input|select|textarea|button)\b[^>]*>""", RegexOption.IGNORE_CASE)
              .findAll(lastBody).map { it.value }.toList()
            log("  JS Redirects: $jsRedirects | Meta Refresh: $metaRefresh | Form Actions: $formActions")
            log("  Inputs/Buttons on Destination (${inputs.size}):")
            inputs.take(25).forEach { log("    [DOM] $it") }

            val nextJsUrl = (jsRedirects + metaRefresh).firstOrNull { (it.startsWith("http") || it.startsWith("/")) && !it.contains("javascript:") }
            if (nextJsUrl != null && hop < 10) {
              val resolvedNext = try { java.net.URI(currentUrl).resolve(nextJsUrl).toString() } catch (_: Exception) { nextJsUrl }
              if (resolvedNext != currentUrl) {
                prevUrl = currentUrl
                currentUrl = resolvedNext
                continue
              }
            }
            break
          }
        }
      }
    } catch (e: Exception) {
      log("Diagnostic error: ${e.message}")
      log(e.stackTraceToString())
    } finally {
      val outFile = java.io.File("build/gdfqo_inspection.txt")
      outFile.parentFile?.mkdirs()
      outFile.writeText(sb.toString())
    }
  }
}
