package com.ashutosh.dotnetinterviewhub;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Shared limits and fail-closed XML parsing for documents selected by the user. */
public final class DocxSecurity {
    public static final int MAX_INPUT_BYTES = 20 * 1024 * 1024;
    public static final int MAX_XML_ENTRY_BYTES = 8 * 1024 * 1024;
    public static final int MAX_MEDIA_ENTRY_BYTES = 12 * 1024 * 1024;
    public static final int MAX_TOTAL_MEDIA_BYTES = 12 * 1024 * 1024;
    public static final int MAX_TOTAL_EXTRACTED_BYTES = 40 * 1024 * 1024;
    public static final int MAX_ZIP_ENTRIES = 1000;

    private DocxSecurity() {}

    public static Document parseXml(byte[] xml) throws Exception {
        if (xml == null || xml.length == 0 || xml.length > MAX_XML_ENTRY_BYTES) {
            throw new IllegalArgumentException("The DOCX contains an invalid or oversized XML part.");
        }
        String declarations = new String(xml, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        if (declarations.contains("<!DOCTYPE") || declarations.contains("<!ENTITY")) {
            throw new IllegalArgumentException("The DOCX contains an unsafe XML declaration.");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        try { factory.setXIncludeAware(false); } catch (UnsupportedOperationException ignored) {}
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException exception) throws SAXException { throw exception; }
            @Override public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
        });
        return builder.parse(new ByteArrayInputStream(xml));
    }

    private static void setFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try { factory.setFeature(name, value); }
        catch (Exception ignored) {
            // Android XML providers differ. The entity resolver remains the final external-access block.
        }
    }

    public static byte[] readLimited(InputStream input, int limit, String message) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException(message);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
