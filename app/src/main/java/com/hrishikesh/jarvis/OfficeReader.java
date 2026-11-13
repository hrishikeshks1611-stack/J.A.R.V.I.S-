package com.hrishikesh.jarvis;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Text extraction for plain text and common Office Open XML attachments. */
public final class OfficeReader {
    private OfficeReader() {}

    public static String read(InputStream input, String displayName) throws IOException {
        if (input == null) throw new IOException("No attachment stream");
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".md")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        if (name.endsWith(".docx") || name.endsWith(".pptx") || name.endsWith(".xlsx")) {
            StringBuilder result = new StringBuilder();
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String path = entry.getName();
                    boolean relevant = (name.endsWith(".docx") && path.equals("word/document.xml"))
                        || (name.endsWith(".pptx") && path.matches("ppt/slides/slide[0-9]+\\.xml"))
                        || (name.endsWith(".xlsx") && path.matches("xl/worksheets/sheet[0-9]+\\.xml"));
                    if (relevant) {
                        ByteArrayOutputStream xml = new ByteArrayOutputStream();
                        byte[] b = new byte[4096]; int n;
                        while ((n = zip.read(b)) != -1) xml.write(b, 0, n);
                        result.append(xmlToText(new String(xml.toByteArray(), StandardCharsets.UTF_8))).append('\n');
                    }
                    zip.closeEntry();
                }
            }
            if (result.length() == 0) throw new IOException("No readable text found in this Office file");
            return result.toString().trim();
        }
        throw new IOException("Supported attachments: TXT, CSV, Markdown, DOCX, PPTX and XLSX. PDF text extraction is not yet supported.");
    }

    private static String xmlToText(String xml) {
        return xml.replaceAll("(?i)</w:p>|</a:p>|</row>|</c>", "\\n")
            .replaceAll("<[^>]+>", "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replaceAll("[ \\t]+", " ").trim();
    }
}
