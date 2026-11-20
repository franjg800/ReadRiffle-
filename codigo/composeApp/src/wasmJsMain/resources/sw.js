// Service worker de Book Room: guarda la app para abrirla sin conexión. Los libros no pasan por
// aquí: viven en el almacenamiento del navegador (IndexedDB).
const CACHE = 'bookroom-v1';
const SIEMPRE_RED = /\/(index\.html|bookroom\.js|manifest\.webmanifest|bridge\.js|instalar\.js|config\.js)?$/;

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(['./', 'index.html', 'bookroom.js', 'bridge.js', 'fflate.js', 'instalar.js', 'config.js', 'icono-180.png', 'icono-192.png'])).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const r = e.request;
  if (r.method !== 'GET') return;
  const u = new URL(r.url);
  if (u.origin !== location.origin) return;
  if (r.mode === 'navigate' || SIEMPRE_RED.test(u.pathname)) {
    // La página y el arranque, de la red si hay (para recibir las versiones nuevas); si no, lo guardado.
    e.respondWith(fetch(r).then(res => { const c = res.clone(); caches.open(CACHE).then(k => k.put(r, c)); return res; }).catch(() => caches.match(r).then(x => x || caches.match('index.html'))));
    return;
  }
  // El resto (wasm con nombre único por versión, fuentes, iconos) no cambia: primero lo guardado.
  e.respondWith(caches.match(r).then(x => x || fetch(r).then(res => {
    if (res.ok) { const c = res.clone(); caches.open(CACHE).then(k => k.put(r, c)); }
    return res;
  })));
});
