// Puente entre Book Room (Kotlin compilado a WebAssembly) y el navegador: almacenamiento de libros
// en IndexedDB, lectura de EPUB (ZIP), red, selector de archivos, compartir, botón atrás e inicio
// de sesión de Google para Drive. Los datos binarios cruzan la frontera como texto "latin1"
// (un carácter por byte), que es mucho más rápido que copiarlos byte a byte.
(function () {
  'use strict';

  // ------------------------------------------------------------------ Bytes <-> latin1
  function bytesToLatin1(u8) {
    let out = '';
    const CH = 0x8000;
    for (let i = 0; i < u8.length; i += CH) out += String.fromCharCode.apply(null, u8.subarray(i, i + CH));
    return out;
  }
  function latin1ToBytes(s) {
    const u8 = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) u8[i] = s.charCodeAt(i) & 0xff;
    return u8;
  }

  // ------------------------------------------------------------------ IndexedDB (archivos)
  const DB_NAME = 'bookroom';
  const STORE = 'files';
  let dbPromise = null;
  function db() {
    if (!dbPromise) {
      dbPromise = new Promise((res, rej) => {
        const r = indexedDB.open(DB_NAME, 1);
        r.onupgradeneeded = () => { r.result.createObjectStore(STORE); };
        r.onsuccess = () => res(r.result);
        r.onerror = () => rej(r.error);
      });
    }
    return dbPromise;
  }
  function tx(mode) { return db().then(d => d.transaction(STORE, mode).objectStore(STORE)); }
  function req(r) { return new Promise((res, rej) => { r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); }); }

  const br = {
    _loadedKeys: [], _loadedValues: [],

    // Carga todas las claves y, de las que no son "perezosas", también su contenido.
    async idbLoadAll(lazyPrefixesCsv) {
      const lazy = lazyPrefixesCsv ? lazyPrefixesCsv.split(',') : [];
      const s = await tx('readonly');
      const keys = await req(s.getAllKeys());
      const values = [];
      for (const k of keys) {
        if (lazy.some(p => k.startsWith(p))) { values.push(null); continue; }
        const v = await req((await tx('readonly')).get(k));
        values.push(v ? bytesToLatin1(v instanceof Uint8Array ? v : new Uint8Array(v)) : null);
      }
      br._loadedKeys = keys; br._loadedValues = values;
      try { if (navigator.storage && navigator.storage.persist) navigator.storage.persist(); } catch (e) {}
      return keys.length;
    },
    loadedKey(i) { return br._loadedKeys[i]; },
    loadedValue(i) { return br._loadedValues[i]; },
    loadedIsLazy(i) { return br._loadedValues[i] === null; },
    loadedClear() { br._loadedKeys = []; br._loadedValues = []; },

    idbPut(key, latin1) {
      const bytes = latin1ToBytes(latin1);
      tx('readwrite').then(s => s.put(bytes, key)).catch(e => console.error('idbPut', key, e));
    },
    idbDelete(key) { tx('readwrite').then(s => s.delete(key)).catch(e => console.error('idbDelete', key, e)); },
    async idbGet(key) {
      const v = await req((await tx('readonly')).get(key));
      return v ? bytesToLatin1(v instanceof Uint8Array ? v : new Uint8Array(v)) : null;
    },
    async storageEstimate() {
      try { const e = await navigator.storage.estimate(); return JSON.stringify({ usage: e.usage || 0, quota: e.quota || 0 }); } catch (e) { return '{}'; }
    },

    // ------------------------------------------------------------------ ZIP (EPUB)
    _zips: {}, _zipSeq: 1,
    zipOpen(latin1) {
      try {
        const files = fflate.unzipSync(latin1ToBytes(latin1));
        const h = br._zipSeq++;
        br._zips[h] = files;
        return h;
      } catch (e) { console.error('zipOpen', e); return 0; }
    },
    zipNames(h) { return Object.keys(br._zips[h] || {}).join('\n'); },
    zipSize(h, name) { const f = (br._zips[h] || {})[name]; return f ? f.length : -1; },
    zipReadText(h, name) { const f = (br._zips[h] || {})[name]; return f ? new TextDecoder('utf-8').decode(f) : null; },
    zipReadBytes(h, name) { const f = (br._zips[h] || {})[name]; return f ? bytesToLatin1(f) : null; },
    zipClose(h) { delete br._zips[h]; },

    // ------------------------------------------------------------------ Texto y URLs
    decodeUri(s) { try { return decodeURIComponent(s); } catch (e) { return s; } },
    encodeUri(s) { return encodeURIComponent(s); },
    stripAccents(s) { return s.normalize('NFD').replace(/[̀-ͯ]/g, ''); },
    uuid() { return (crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16); })); },
    now() { return Date.now(); },
    tzOffsetMinutes(ms) { return -new Date(ms).getTimezoneOffset(); },

    // ------------------------------------------------------------------ Red
    async fetch(method, url, headersJson, body, bodyIsText) {
      const headers = headersJson ? JSON.parse(headersJson) : {};
      const init = { method, headers };
      if (body !== null && body !== undefined) init.body = bodyIsText ? body : latin1ToBytes(body);
      try {
        const r = await fetch(url, init);
        const buf = new Uint8Array(await r.arrayBuffer());
        return { status: r.status, body: bytesToLatin1(buf), error: '' };
      } catch (e) {
        return { status: 0, body: '', error: String(e && e.message || e) };
      }
    },
    // JSONP: para APIs que no permiten peticiones desde otras webs (CORS) pero sí ?callback= (Apple).
    jsonp(url) {
      return new Promise(res => {
        const cb = 'brjp' + Date.now() + Math.floor(Math.random() * 1e6);
        const s = document.createElement('script');
        const cleanup = () => { clearTimeout(t); try { delete window[cb]; } catch (e) {} s.remove(); };
        const t = setTimeout(() => { cleanup(); res(''); }, 12000);
        window[cb] = d => { cleanup(); res(JSON.stringify(d)); };
        s.onerror = () => { cleanup(); res(''); };
        s.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + 'callback=' + cb;
        document.head.appendChild(s);
      });
    },
    utf8ToLatin1(s) { return bytesToLatin1(new TextEncoder().encode(s)); },
    latin1ToUtf8(s) { return new TextDecoder('utf-8').decode(latin1ToBytes(s)); },

    // ------------------------------------------------------------------ localStorage
    lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) {} },
    lsRemove(k) { try { localStorage.removeItem(k); } catch (e) {} },

    // ------------------------------------------------------------------ Archivos del usuario
    _picked: [],
    pickFiles(accept, multiple, cb) {
      const i = document.createElement('input');
      i.type = 'file'; if (accept) i.accept = accept; i.multiple = !!multiple;
      i.style.position = 'fixed'; i.style.left = '-1000px';
      document.body.appendChild(i);
      i.onchange = async () => {
        const files = Array.from(i.files || []);
        const out = [];
        for (const f of files) {
          try { out.push({ name: f.name, data: bytesToLatin1(new Uint8Array(await f.arrayBuffer())) }); } catch (e) { console.error(e); }
        }
        br._picked = out;
        i.remove();
        cb(out.length);
      };
      i.click();
    },
    pickedName(n) { return br._picked[n] ? br._picked[n].name : ''; },
    pickedData(n) { return br._picked[n] ? br._picked[n].data : ''; },
    pickedClear() { br._picked = []; },

    download(name, latin1, mime) {
      const blob = new Blob([latin1ToBytes(latin1)], { type: mime || 'application/octet-stream' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 10000);
    },
    openUrl(url) { try { window.open(url, '_blank', 'noopener'); } catch (e) { location.href = url; } },
    async shareText(text, title) {
      try { if (navigator.share) { await navigator.share({ text, title: title || undefined }); return 'ok'; } } catch (e) { if (e && e.name === 'AbortError') return 'cancel'; }
      try { await navigator.clipboard.writeText(text); return 'copied'; } catch (e) { return 'fail'; }
    },
    async shareImage(name, latin1, text) {
      const archivo = new File([latin1ToBytes(latin1)], name + '.png', { type: 'image/png' });
      try {
        if (navigator.canShare && navigator.canShare({ files: [archivo] })) { await navigator.share({ files: [archivo], text: text || undefined }); return 'ok'; }
      } catch (e) { if (e && e.name === 'AbortError') return 'cancel'; }
      br.download(archivo.name, latin1, 'image/png');
      return 'downloaded';
    },
    async copyText(t) { try { await navigator.clipboard.writeText(t); return true; } catch (e) { return false; } },
    vibrate(ms) { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} },

    // ------------------------------------------------------------------ Apariencia
    color(c) {
      const m = document.querySelector('meta[name="theme-color"]'); if (m) m.setAttribute('content', c);
      document.body.style.background = c; document.documentElement.style.background = c;
    },
    keepAwake(on) {
      try {
        if (on && 'wakeLock' in navigator) { navigator.wakeLock.request('screen').then(l => { br._lock = l; }).catch(() => {}); }
        else if (!on && br._lock) { br._lock.release(); br._lock = null; }
      } catch (e) {}
    },
    isStandalone() { return window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true; },
    origin() { return location.origin + location.pathname.replace(/[^/]*$/, ''); },

    // ------------------------------------------------------------------ Botón atrás del navegador
    _volver: null, _activo: false, _puesto: false,
    alVolver(cb) { br._volver = cb; },
    atrasActivo(a) { br._activo = a; if (a && !br._puesto) { history.pushState({ br: 1 }, ''); br._puesto = true; } },

    // ------------------------------------------------------------------ Google (Drive)
    // Flujo OAuth "token" por redirección: funciona igual en Safari y con la app instalada.
    oauthStart(clientId, scope, prompt, hint) {
      const redirect = br.origin();
      const state = Math.random().toString(36).slice(2);
      try { sessionStorage.setItem('br_oauth_state', state); localStorage.setItem('br_oauth_state', state); } catch (e) {}
      const u = 'https://accounts.google.com/o/oauth2/v2/auth?' + new URLSearchParams({
        client_id: clientId, redirect_uri: redirect, response_type: 'token', scope,
        include_granted_scopes: 'true', state, prompt: prompt || 'consent', ...(hint ? { login_hint: hint } : {})
      }).toString();
      location.href = u;
    },
    // Al volver de Google, el token llega en el fragmento (#access_token=...). Se guarda y se limpia la URL.
    oauthCapture() {
      const h = location.hash;
      if (!h || h.indexOf('access_token=') < 0 && h.indexOf('error=') < 0) return '';
      const p = new URLSearchParams(h.slice(1));
      history.replaceState(null, '', location.pathname + location.search);
      const out = { token: p.get('access_token') || '', expiresIn: +(p.get('expires_in') || 0), error: p.get('error') || '' };
      return JSON.stringify(out);
    },
  };

  // Al salir de la app (otra app, bloquear el móvil, cerrar la pestaña) se guarda el progreso.
  br.onHidden = function (cb) { br._hidden = cb; };
  const avisarOculta = () => { try { br._hidden && br._hidden(); } catch (e) { console.error(e); } };
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') avisarOculta(); });
  addEventListener('pagehide', avisarOculta);

  addEventListener('popstate', () => {
    br._puesto = false;
    if (br._volver && br._activo) br._volver();
    setTimeout(() => { if (br._activo && !br._puesto) { history.pushState({ br: 1 }, ''); br._puesto = true; } }, 60);
  });
  addEventListener('keydown', e => {
    if (e.key !== 'Escape' || e.defaultPrevented) return;
    const ins = document.getElementById('ins');
    if (ins && !ins.hidden) return;
    if (br._volver && br._activo) { e.preventDefault(); br._volver(); }
  });

  window.br = br;
})();
