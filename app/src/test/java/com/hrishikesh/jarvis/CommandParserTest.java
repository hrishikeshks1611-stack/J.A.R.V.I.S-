package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandParserTest {
    @Test public void recognisesCameraAndSelfieCommands() {
        assertEquals(CommandParser.Action.OPEN_CAMERA, CommandParser.parse("open camera").action);
        assertEquals(CommandParser.Action.OPEN_CAMERA, CommandParser.parse("take a photo").action);
        assertEquals(CommandParser.Action.OPEN_CAMERA, CommandParser.parse("take a selfie").action);
    }

    @Test public void extractsAppNameWithoutChangingIt() {
        CommandParser.Command c = CommandParser.parse("Open WhatsApp");
        assertEquals(CommandParser.Action.OPEN_APP, c.action);
        assertEquals("whatsapp", c.argument);
    }

    @Test public void extractsMusicQuery() {
        CommandParser.Command c = CommandParser.parse("Play Back in Black");
        assertEquals(CommandParser.Action.PLAY_MUSIC, c.action);
        assertEquals("back in black", c.argument);
    }

    @Test public void recognisesWakePhrases() {
        assertEquals(CommandParser.Action.GREETING, CommandParser.parse("Hey JARVIS").action);
        assertEquals(CommandParser.Action.GREETING, CommandParser.parse("Wake up, JARVIS").action);
        assertEquals(CommandParser.Action.GREETING, CommandParser.parse("JARVIS, are you there?").action);
    }

    @Test public void recognisesMediaSessionCommands() {
        assertEquals(CommandParser.Action.PAUSE_MEDIA, CommandParser.parse("pause music").action);
        assertEquals(CommandParser.Action.RESUME_MEDIA, CommandParser.parse("resume music").action);
        assertEquals(CommandParser.Action.NEXT_TRACK, CommandParser.parse("next song").action);
        assertEquals(CommandParser.Action.PREVIOUS_TRACK, CommandParser.parse("previous track").action);
    }

    @Test public void unknownAndEmptyCommandsAreSafe() {
        assertEquals(CommandParser.Action.UNKNOWN, CommandParser.parse(null).action);
        assertEquals(CommandParser.Action.UNKNOWN, CommandParser.parse("   ").action);
        assertEquals(CommandParser.Action.UNKNOWN, CommandParser.parse("send an email").action);
    }
}
