@file:Suppress("unused")

package com.readriffle.app.platform

import kotlin.js.Promise

// Declaraciones del puente con el navegador (ver resources/bridge.js).

@JsFun("() => Date.now()")
internal external fun jsNow(): Double

@JsFun("(ms) => window.br.tzOffsetMinutes(ms)")
internal external fun jsTzOffsetMinutes(ms: Double): Int

@JsFun("() => window.br.uuid()")
internal external fun jsUuid(): String

@JsFun("(s) => window.br.decodeUri(s)")
internal external fun jsDecodeUri(s: String): String

@JsFun("(s) => window.br.encodeUri(s)")
internal external fun jsEncodeUri(s: String): String

@JsFun("(s) => window.br.stripAccents(s)")
internal external fun jsStripAccents(s: String): String

// --- IndexedDB
@JsFun("(lazy) => window.br.idbLoadAll(lazy)")
internal external fun jsIdbLoadAll(lazyPrefixesCsv: String): Promise<JsNumber>

@JsFun("(i) => window.br.loadedKey(i)")
internal external fun jsLoadedKey(i: Int): String

@JsFun("(i) => window.br.loadedValue(i)")
internal external fun jsLoadedValue(i: Int): String?

@JsFun("(i) => window.br.loadedIsLazy(i)")
internal external fun jsLoadedIsLazy(i: Int): Boolean

@JsFun("() => window.br.loadedClear()")
internal external fun jsLoadedClear()

@JsFun("(k, v) => window.br.idbPut(k, v)")
internal external fun jsIdbPut(key: String, latin1: String)

@JsFun("(k) => window.br.idbDelete(k)")
internal external fun jsIdbDelete(key: String)

@JsFun("(k) => window.br.idbGet(k)")
internal external fun jsIdbGet(key: String): Promise<JsString?>

@JsFun("() => window.br.storageEstimate()")
internal external fun jsStorageEstimate(): Promise<JsString>

// --- ZIP
@JsFun("(s) => window.br.zipOpen(s)")
internal external fun jsZipOpen(latin1: String): Int

@JsFun("(h) => window.br.zipNames(h)")
internal external fun jsZipNames(h: Int): String

@JsFun("(h, n) => window.br.zipSize(h, n)")
internal external fun jsZipSize(h: Int, name: String): Int

@JsFun("(h, n) => window.br.zipReadText(h, n)")
internal external fun jsZipReadText(h: Int, name: String): String?

@JsFun("(h, n) => window.br.zipReadBytes(h, n)")
internal external fun jsZipReadBytes(h: Int, name: String): String?

@JsFun("(h) => window.br.zipClose(h)")
internal external fun jsZipClose(h: Int)

// --- Red
internal external interface JsFetchResult : JsAny {
    val status: Int
    val body: String
    val error: String
}

@JsFun("(m, u, h, b, t) => window.br.fetch(m, u, h, b, t)")
internal external fun jsFetch(method: String, url: String, headersJson: String?, body: String?, bodyIsText: Boolean): Promise<JsFetchResult>

@JsFun("(s) => window.br.utf8ToLatin1(s)")
internal external fun jsUtf8ToLatin1(s: String): String

@JsFun("(s) => window.br.latin1ToUtf8(s)")
internal external fun jsLatin1ToUtf8(s: String): String

// --- localStorage
@JsFun("(k) => window.br.lsGet(k)")
internal external fun jsLsGet(k: String): String?

@JsFun("(k, v) => window.br.lsSet(k, v)")
internal external fun jsLsSet(k: String, v: String)

@JsFun("(k) => window.br.lsRemove(k)")
internal external fun jsLsRemove(k: String)

// --- Archivos, compartir, navegador
@JsFun("(a, m, cb) => window.br.pickFiles(a, m, cb)")
internal external fun jsPickFiles(accept: String, multiple: Boolean, cb: (Int) -> Unit)

@JsFun("(n) => window.br.pickedName(n)")
internal external fun jsPickedName(n: Int): String

@JsFun("(n) => window.br.pickedData(n)")
internal external fun jsPickedData(n: Int): String

@JsFun("() => window.br.pickedClear()")
internal external fun jsPickedClear()

@JsFun("(n, d, m) => window.br.download(n, d, m)")
internal external fun jsDownload(name: String, latin1: String, mime: String)

@JsFun("(u) => window.br.openUrl(u)")
internal external fun jsOpenUrl(url: String)

@JsFun("(t, ti) => window.br.shareText(t, ti)")
internal external fun jsShareText(text: String, title: String?): Promise<JsString>

@JsFun("(n, d, t) => window.br.shareImage(n, d, t)")
internal external fun jsShareImage(name: String, latin1: String, text: String?): Promise<JsString>

@JsFun("(t) => window.br.copyText(t)")
internal external fun jsCopyText(t: String): Promise<JsBoolean>

@JsFun("(c) => window.br.color(c)")
internal external fun jsThemeColor(c: String)

@JsFun("(on) => window.br.keepAwake(on)")
internal external fun jsKeepAwake(on: Boolean)

@JsFun("() => window.br.isStandalone()")
internal external fun jsIsStandalone(): Boolean

@JsFun("() => window.br.origin()")
internal external fun jsOrigin(): String

@JsFun("(cb) => window.br.alVolver(cb)")
internal external fun jsAlVolver(cb: () -> Unit)

@JsFun("(a) => window.br.atrasActivo(a)")
internal external fun jsAtrasActivo(activo: Boolean)

@JsFun("(c, s, p, h) => window.br.oauthStart(c, s, p, h)")
internal external fun jsOauthStart(clientId: String, scope: String, prompt: String, hint: String?)

@JsFun("() => (window.BOOKROOM_CONFIG && window.BOOKROOM_CONFIG.googleClientId) || ''")
internal external fun jsConfiguredClientId(): String

@JsFun("() => window.br.oauthCapture()")
internal external fun jsOauthCapture(): String

@JsFun("(ms) => window.br.vibrate(ms)")
internal external fun jsVibrate(ms: Int)

@JsFun("() => window.bookroomInstalar && window.bookroomInstalar.abrir()")
internal external fun jsOpenInstallGuide()

@JsFun("(cb) => window.br.onHidden(cb)")
internal external fun jsOnHidden(cb: () -> Unit)

@JsFun("(u) => window.br.jsonp(u)")
internal external fun jsJsonp(url: String): Promise<JsString>

@JsFun("() => (window.BOOKROOM_CONFIG && window.BOOKROOM_CONFIG.contactEmail) || ''")
internal external fun jsContactEmail(): String

@JsFun("() => navigator.onLine !== false")
internal external fun jsOnline(): Boolean

@JsFun("() => window.bookroomInstalar && window.bookroomInstalar.primeraVez()")
internal external fun jsInstallFirstTime()

@JsFun("(cb) => window.br.onKey(cb)")
internal external fun jsOnKey(cb: (String) -> Boolean)
