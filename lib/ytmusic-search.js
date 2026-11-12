const YTM_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";
const SONG_FILTER = "EgWKAQIIAWoKEAoQCRADEAQQBQ==";

function textValue(value) {
  if (!value || typeof value !== "object") return "";
  if (typeof value.simpleText === "string") return value.simpleText;
  if (Array.isArray(value.runs)) return value.runs.map(run => String(run?.text || "")).join("").trim();
  return "";
}

function findVideoId(value, seen = new Set()) {
  if (!value || typeof value !== "object" || seen.has(value)) return "";
  seen.add(value);
  if (typeof value.videoId === "string" && /^[a-zA-Z0-9_-]{11}$/.test(value.videoId)) return value.videoId;
  for (const child of Object.values(value)) {
    const found = findVideoId(child, seen);
    if (found) return found;
  }
  return "";
}

function collectSongRenderers(value, results = [], seen = new Set()) {
  if (!value || typeof value !== "object" || seen.has(value) || results.length >= 10) return results;
  seen.add(value);
  if (value.musicResponsiveListItemRenderer) {
    results.push(value.musicResponsiveListItemRenderer);
    if (results.length >= 10) return results;
  }
  for (const child of Object.values(value)) collectSongRenderers(child, results, seen);
  return results;
}

function columnText(column) {
  return textValue(column?.musicResponsiveListItemFlexColumnRenderer?.text);
}

export function parseYouTubeMusicSongs(payload) {
  const renderers = collectSongRenderers(payload);
  const items = [];
  const seenIds = new Set();
  for (const renderer of renderers) {
    const videoId = findVideoId(renderer);
    if (!videoId || seenIds.has(videoId)) continue;
    const columns = Array.isArray(renderer.flexColumns) ? renderer.flexColumns : [];
    const title = columnText(columns[0]) || "YouTube Music track";
    const subtitle = columnText(columns[1]);
    const artist = subtitle.split(/\s*[•·]\s*/).filter(Boolean).slice(0, 1).join("") || "Unknown artist";
    const duration = columnText(columns[2]);
    seenIds.add(videoId);
    items.push({
      videoId,
      title: title.slice(0, 180),
      artist: artist.slice(0, 180),
      duration: duration.slice(0, 24),
      url: "https://music.youtube.com/watch?v=" + videoId
    });
    if (items.length >= 5) break;
  }
  return items;
}

export async function searchYouTubeMusicSongs(query, fetchImpl = fetch) {
  const cleanQuery = String(query || "").trim().slice(0, 160);
  if (!cleanQuery) throw new Error("A song title is required.");
  const today = new Date().toISOString().slice(0, 10).replaceAll("-", "");
  const response = await fetchImpl(YTM_SEARCH_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept": "application/json",
      "Origin": "https://music.youtube.com",
      "Referer": "https://music.youtube.com/",
      "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    },
    body: JSON.stringify({
      context: {
        client: { clientName: "WEB_REMIX", clientVersion: "1." + today + ".01.00", hl: "en", gl: "IN" },
        user: {}
      },
      query: cleanQuery,
      params: SONG_FILTER
    }),
    signal: AbortSignal.timeout(6500)
  });
  if (!response.ok) throw new Error("YouTube Music search returned HTTP " + response.status + ".");
  const payload = await response.json();
  return parseYouTubeMusicSongs(payload);
}
