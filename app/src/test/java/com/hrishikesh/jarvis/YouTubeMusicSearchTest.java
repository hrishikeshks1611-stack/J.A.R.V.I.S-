package com.hrishikesh.jarvis;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class YouTubeMusicSearchTest {
    @Test public void extractsSongTitleArtistAndVideoId() throws Exception {
        JSONObject payload = new JSONObject(
            "{\"contents\":{\"musicResponsiveListItemRenderer\":{\"flexColumns\":[" +
            "{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{\"runs\":[{\"text\":\"Numb\"}]}}}," +
            "{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{\"simpleText\":\"Linkin Park • Meteora • 2003\"}}}," +
            "{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{\"simpleText\":\"3:07\"}}}" +
            "],\"navigationEndpoint\":{\"watchEndpoint\":{\"videoId\":\"kXYiU_JCYtU\"}}}}}}");
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
        JSONObject payload = new JSONObject(
            "{\"a\":{\"musicResponsiveListItemRenderer\":{\"flexColumns\":[{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{\"simpleText\":\"One\"}}}],\"videoId\":\"kXYiU_JCYtU\"}}," +
            "\"b\":{\"musicResponsiveListItemRenderer\":{\"flexColumns\":[{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{\"simpleText\":\"Duplicate\"}}}],\"videoId\":\"kXYiU_JCYtU\"}}," +
            "\"c\":{\"musicResponsiveListItemRenderer\":{\"videoId\":\"bad\"}}}");
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
