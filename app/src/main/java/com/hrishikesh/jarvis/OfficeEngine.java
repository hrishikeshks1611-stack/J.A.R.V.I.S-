package com.hrishikesh.jarvis;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Lightweight offline Office exporter. Produces real OOXML packages (DOCX/PPTX/XLSX)
 * and a standards-based single-page PDF. Files are written to app-private cache first;
 * the Activity must copy them to a user-selected SAF destination.
 */
public final class OfficeEngine {
    public enum Format { DOCX, PPTX, XLSX, PDF }
    private OfficeEngine() {}

    public static File create(File directory, Format format, String title, String body) throws IOException {
        if (directory == null || (!directory.exists() && !directory.mkdirs())) throw new IOException("Cannot create output directory");
        String safe = (title == null ? "JARVIS_Document" : title).replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.isEmpty()) safe = "JARVIS_Document";
        File out = new File(directory, safe + extension(format));
        switch (format) {
            case DOCX: writeDocx(out, title, body); break;
            case PPTX: writePptx(out, title, body); break;
            case XLSX: writeXlsx(out, title, body); break;
            case PDF: writePdf(out, title, body); break;
            default: throw new IOException("Unsupported format");
        }
        if (!out.isFile() || out.length() < 100) throw new IOException("Export validation failed");
        return out;
    }

    private static String extension(Format f) {
        switch (f) { case DOCX: return ".docx"; case PPTX: return ".pptx"; case XLSX: return ".xlsx"; default: return ".pdf"; }
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    private static void entry(ZipOutputStream z, String name, String value) throws IOException {
        z.putNextEntry(new ZipEntry(name)); z.write(value.getBytes(StandardCharsets.UTF_8)); z.closeEntry();
    }
    private static void writeDocx(File f, String title, String body) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            entry(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            StringBuilder p = new StringBuilder();
            p.append("<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr><w:r><w:t>").append(esc(title)).append("</w:t></w:r></w:p>");
            for (String line : (body == null ? "" : body).split("\\r?\\n",-1))
                p.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r></w:p>");
            entry(z,"word/document.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"+p+"<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>");
        }
    }
    private static void writePptx(File f, String title, String body) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/><Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/></Types>");
            entry(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>");
            entry(z,"ppt/presentation.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId1\"/></p:sldIdLst><p:sldSz cx=\"9144000\" cy=\"5143500\"/></p:presentation>");
            entry(z,"ppt/_rels/presentation.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide1.xml\"/></Relationships>");
            StringBuilder shapes = new StringBuilder();
            shapes.append(shape(2,title,457200,300000,8200000,900000));
            String[] lines=(body==null?"":body).split("\\r?\\n");
            StringBuilder text=new StringBuilder(); for(String line:lines) text.append(line).append("\n");
            shapes.append(shape(3,text.toString(),457200,1450000,8200000,3300000));
            entry(z,"ppt/slides/slide1.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:cSld><p:spTree><p:nvGrpSpPr/><p:grpSpPr/>"+shapes+"</p:spTree></p:cSld></p:sld>");
        }
    }
    private static String shape(int id,String text,int x,int y,int cx,int cy) {
        StringBuilder runs=new StringBuilder();
        String[] lines=(text==null?"":text).split("\\r?\\n",-1);
        for(String line:lines) runs.append("<a:p><a:r><a:rPr lang=\"en-US\"/><a:t>").append(esc(line)).append("</a:t></a:r><a:endParaRPr lang=\"en-US\"/></a:p>");
        return "<p:sp><p:nvSpPr><p:cNvPr id=\""+id+"\" name=\"TextBox "+id+"\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\""+x+"\" y=\""+y+"\"/><a:ext cx=\""+cx+"\" cy=\""+cy+"\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr><p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>"+runs+"</p:txBody></p:sp>";
    }
    private static void writeXlsx(File f,String title,String body) throws IOException {
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
            entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"JARVIS Data\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder rows=new StringBuilder(); int row=1;
            rows.append(row(row++,title));
            for(String line:(body==null?"":body).split("\\r?\\n")) rows.append(row(row++,line));
            entry(z,"xl/worksheets/sheet1.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"+rows+"</sheetData></worksheet>");
        }
    }
    private static String row(int n,String value){return "<row r=\""+n+"\"><c r=\"A"+n+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+esc(value)+"</t></is></c></row>";}

    private static void writePdf(File f,String title,String body) throws IOException {
        List<String> lines=new ArrayList<>(); lines.add(title==null?"JARVIS Document":title);
        for(String line:(body==null?"":body).split("\\r?\\n")) {
            String safe=line.replaceAll("[^\\x20-\\x7E]","?");
            while(safe.length()>88){lines.add(safe.substring(0,88));safe=safe.substring(88);} lines.add(safe);
        }
        StringBuilder stream=new StringBuilder("BT\n/F1 16 Tf\n50 790 Td\n");
        for(int i=0;i<lines.size() && i<48;i++){ if(i==1) stream.append("/F1 11 Tf\n"); stream.append("(").append(pdfEscape(lines.get(i))).append(") Tj\n0 -15 Td\n");}
        stream.append("ET");
        byte[] content=stream.toString().getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream b=new ByteArrayOutputStream(); List<Integer> offsets=new ArrayList<>();
        writeAscii(b,"%PDF-1.4\n");
        String[] objs={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",null};
        for(int i=0;i<5;i++){offsets.add(b.size());writeAscii(b,(i+1)+" 0 obj\n");if(i==4){writeAscii(b,"<< /Length "+content.length+" >>\nstream\n");b.write(content);writeAscii(b,"\nendstream\n");}else writeAscii(b,objs[i]+"\n");writeAscii(b,"endobj\n");}
        int xref=b.size();writeAscii(b,"xref\n0 6\n0000000000 65535 f \n");
        for(int off:offsets)writeAscii(b,String.format(Locale.US,"%010d 00000 n \n",off));
        writeAscii(b,"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF");
        try(FileOutputStream out=new FileOutputStream(f)){b.writeTo(out);}
    }
    private static String pdfEscape(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)");}
    private static void writeAscii(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.US_ASCII));}
}
