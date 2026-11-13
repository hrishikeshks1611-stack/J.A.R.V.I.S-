package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.zip.*;

public class OfficeEngineTest {
    @Test public void createsAllFourFormats() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "jarvis-office-test");
        assertTrue(dir.exists() || dir.mkdirs());
        for (OfficeEngine.Format format : OfficeEngine.Format.values()) {
            File file = OfficeEngine.create(dir, format, "Sample", "First line\nSecond line");
            assertTrue(file.isFile());
            assertTrue(file.length() > 100);
            if (format != OfficeEngine.Format.PDF) {
                try (ZipFile zip = new ZipFile(file)) {
                    assertNotNull(zip.getEntry("[Content_Types].xml"));
                }
            } else {
                try (FileInputStream in = new FileInputStream(file)) {
                    byte[] header = new byte[8];
                    assertEquals(8, in.read(header));
                    assertEquals("%PDF-1.4", new String(header, "US-ASCII"));
                }
            }
        }
    }
}
