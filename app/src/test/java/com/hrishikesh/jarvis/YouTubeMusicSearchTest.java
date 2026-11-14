package com.hrishikesh.jarvis;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class YouTubeMusicSearchTest {
    private JSONObject column(String text, boolean useRuns) throws Exception {
        JSONObject value;
        if (useRuns) {
            value = new JSONObject().put("runs", new JSONArray()
                    .put(new JSONObject().put("text", text)));
        } else {
            value = new JSONObject().put("simpleText", text);
        }
        return new JSONObject().put("musicResponsiveListItemFlexColumnRenderer",
                new JSONObject().put("text", value));
    }

    @Test public void extractsSongTitleArtistAndVideoId() throws Exception {
        JSONObject renderer = new JSONObject()
                .put("flexColumns", new JSONArray()
                        .put(column("Numb", true))
                        .put(column("Linkin Park • Meteora • 2003", false))
                        .put(column("3:07", false)))
                .put("navigationEndpoint", new JSONObject()
                        .put("watchEndpoint", new JSONObject().put("videoId", "kXYiU_JCYtU")));
        JSONObject payload = new JSONObject().put("contents",
                new JSONObject().put("musicResponsiveListItemRenderer", renderer));

        JSONArray items = YouTubeMusicSearch.parseItems(payload);
        assertEquals(1, items.length());
        JSONObject song = items.getJSONObject(0);
        assertEquals("kXYiU_JCYtU", song.getString("videoId"));
        assertEquals("Numb", song.getString("title"));
        assertEquals("Linkin Park", song.getString("artist"));
        assertEquals("3:07", song.getString("duration"));
        assertEquals("https://music.youtube.com/watch?v=kXYiU_JCYtU", song.getString("url"));
    }

    @Test public void ignoresInvalidAndDuplicateVideoIds() throws Exception {
        JSONObject first = new JSONObject()
                .put("flexColumns", new JSONArray().put(column("One", false)))
                .put("videoId", "kXYiU_JCYtU");
        JSONObject duplicate = new JSONObject()
                .put("flexColumns", new JSONArray().put(column("Duplicate", false)))
                .put("videoId", "kXYiU_JCYtU");
        JSONObject invalid = new JSONObject().put("videoId", "bad");
        JSONObject payload = new JSONObject()
                .put("a", new JSONObject().put("musicResponsiveListItemRenderer", first))
                .put("b", new JSONObject().put("musicResponsiveListItemRenderer", duplicate))
                .put("c", new JSONObject().put("musicResponsiveListItemRenderer", invalid));

        JSONArray items = YouTubeMusicSearch.parseItems(payload);
        assertEquals(1, items.length());
        assertEquals("One", items.getJSONObject(0).getString("title"));
    }

    @Test public void rejectsEmptyAndOverlongSearchQueries() {
        try {
            YouTubeMusicSearch.search(" ");
            fail("Empty query should fail before any network request.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("song title"));
        } catch (Exception unexpected) {
            fail("Unexpected exception: " + unexpected);
        }
        try {
            YouTubeMusicSearch.search(new String(new char[161]).replace('\0', 'x'));
            fail("Overlong query should fail before any network request.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("160"));
        } catch (Exception unexpected) {
            fail("Unexpected exception: " + unexpected);
        }
    }
}
