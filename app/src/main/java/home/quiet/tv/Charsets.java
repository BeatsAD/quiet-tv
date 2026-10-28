package home.quiet.tv;

import java.nio.charset.Charset;

/** Charset constants available before Android's API19 StandardCharsets class. */
final class Charsets {
    static final Charset UTF_8=Charset.forName("UTF-8");
    static final Charset US_ASCII=Charset.forName("US-ASCII");
    private Charsets(){}
}
