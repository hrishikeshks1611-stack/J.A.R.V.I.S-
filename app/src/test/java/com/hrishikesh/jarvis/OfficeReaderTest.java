package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class OfficeReaderTest {
    @Test public void readsGeneratedWordDocument() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "jarvis-reader-test");
        File doc = OfficeEngine.create(dir, OfficeEngine.Format.DOCX, "Notes", "Alpha topic\nBeta topic");
        try (FileInputStream in = new FileInputStream(doc)) {
            String text = OfficeReader.read(in, "Notes.docx");
            assertTrue(text.contains("Alpha topic"));
            assertTrue(text.contains("Beta topic"));
        }
    }
}
