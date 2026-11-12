import { searchYouTubeMusicSongs } from "../lib/ytmusic-search.js";

const items = await searchYouTubeMusicSongs("Numb Linkin Park");
if (!items.length) throw new Error("YouTube Music returned no parseable song results for the live smoke query.");
const selected = items.find(item => /numb/i.test(item.title) && /linkin park/i.test(item.artist)) || items[0];
if (!/^[a-zA-Z0-9_-]{11}$/.test(selected.videoId)) throw new Error("YouTube Music returned an invalid track ID.");
if (!selected.url.startsWith("https://music.youtube.com/watch?v=")) throw new Error("The selected result did not produce a YouTube Music track URL.");
console.log(JSON.stringify({ ok: true, title: selected.title, artist: selected.artist, videoId: selected.videoId, url: selected.url }));
