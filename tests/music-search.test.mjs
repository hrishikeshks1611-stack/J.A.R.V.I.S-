import test from "node:test";
import assert from "node:assert/strict";
import { parseYouTubeMusicSongs, searchYouTubeMusicSongs } from "../lib/ytmusic-search.js";

test("YouTube Music parser extracts song IDs and metadata from song renderers", () => {
  const payload = {
    contents: { sectionListRenderer: { contents: [{
      musicShelfRenderer: { contents: [
        { musicResponsiveListItemRenderer: {
          flexColumns: [
            { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: "Numb" }] } } },
            { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: "Linkin Park" }, { text: " • " }, { text: "Meteora" }] } } },
            { musicResponsiveListItemFlexColumnRenderer: { text: { simpleText: "3:07" } } }
          ],
          overlay: { musicItemThumbnailOverlayRenderer: { content: { musicPlayButtonRenderer: {
            playNavigationEndpoint: { watchEndpoint: { videoId: "kXYiU_JCYtU" } }
          } } } }
        } }
      ] }
    }] } }
  };
  assert.deepEqual(parseYouTubeMusicSongs(payload), [{
    videoId: "kXYiU_JCYtU",
    title: "Numb",
    artist: "Linkin Park",
    duration: "3:07",
    url: "https://music.youtube.com/watch?v=kXYiU_JCYtU"
  }]);
});

test("YouTube Music parser ignores duplicate IDs and malformed results", () => {
  const renderer = {
    musicResponsiveListItemRenderer: {
      flexColumns: [{ musicResponsiveListItemFlexColumnRenderer: { text: { simpleText: "Song" } } }],
      playlistItemData: { videoId: "abcdefghijk" }
    }
  };
  assert.equal(parseYouTubeMusicSongs({ a: renderer, b: renderer, c: { videoId: "bad" } }).length, 1);
});

test("YouTube Music search sends a bounded unauthenticated catalogue query", async () => {
  let request;
  const fakeFetch = async (url, options) => {
    request = { url, options };
    return { ok: true, json: async () => ({ contents: { musicShelfRenderer: { contents: [] } } }) };
  };
  assert.deepEqual(await searchYouTubeMusicSongs("Numb Linkin Park", fakeFetch), []);
  assert.match(request.url, /^https:\/\/music\.youtube\.com\/youtubei\/v1\/search/);
  assert.equal(request.options.method, "POST");
  const body = JSON.parse(request.options.body);
  assert.equal(body.query, "Numb Linkin Park");
  assert.equal(body.context.client.clientName, "WEB_REMIX");
  assert.equal(body.context.client.gl, "IN");
  assert.ok(request.options.signal);
});

import musicSearchHandler from "../api/music-search.js";

function fakeResponse() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { this.ended = true; return this; }
  };
}

test("music search API handles the approved app preflight without a session", async () => {
  const res = fakeResponse();
  await musicSearchHandler({ method: "OPTIONS", headers: { origin: "https://hrishikeshks1611-stack.github.io" } }, res);
  assert.equal(res.statusCode, 204);
  assert.equal(res.headers["Access-Control-Allow-Origin"], "https://hrishikeshks1611-stack.github.io");
});

test("music search API rejects untrusted origins", async () => {
  const res = fakeResponse();
  await musicSearchHandler({ method: "POST", headers: { origin: "https://untrusted.example" }, body: { query: "Numb" } }, res);
  assert.equal(res.statusCode, 403);
});

test("music search API rejects oversized queries before upstream lookup", async () => {
  const res = fakeResponse();
  await musicSearchHandler({ method: "POST", headers: {}, body: { query: "x".repeat(161) } }, res);
  assert.equal(res.statusCode, 400);
});

test("music search API requires a paired JARVIS session", async () => {
  const res = fakeResponse();
  await musicSearchHandler({ method: "POST", headers: {}, body: { query: "Numb Linkin Park" } }, res);
  assert.equal(res.statusCode, 401);
  assert.match(res.body.error, /not paired/i);
});
