const CACHE="jarvis-shell-v39";
const ASSETS=["./","./index.html","./manifest.webmanifest","./jarvis-icon.svg","./icons/jarvis-192.png","./icons/jarvis-512.png"];

self.addEventListener("install",event=>{
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)));
});

self.addEventListener("activate",event=>{
  event.waitUntil(
    caches.keys()
      .then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key))))
      .then(()=>self.clients.claim())
  );
});

self.addEventListener("fetch",event=>{
  const request=event.request;
  const url=new URL(request.url);
  if(request.method!=="GET" || url.pathname.startsWith("/api/")) return;
  // Always fetch the live application shell. Never serve an older cached index
  // when the network is unavailable; this prevents stale owner controls.
  if(request.mode==="navigate" || url.pathname.endsWith("/index.html") || url.pathname==="/") {
    event.respondWith(fetch(request, {cache:"no-store"}));
    return;
  }
  event.respondWith(caches.match(request).then(cached=>cached || fetch(request).then(response=>{
    if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(request,copy));}
    return response;
  })));
});
