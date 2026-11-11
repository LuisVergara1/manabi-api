package dev.luisvergara.manabi.content.publication.contract;

import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * canonicalText for this protocol: JS UTF-16 key ordering, JSON.stringify indentation,
 * array order retained, UTF-8 and one LF. Not RFC 8785. Protocol numbers are safe integers.
 * Arbitrary objects, entities, sets and floating-point fractions are never reflected/coerced.
 */
public final class CanonicalJson {
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final JsonMapper READER = JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(129)
            .maxStringLength(MAX_BYTES).maxNumberLength(1024).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    public Object parse(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) fail("$", "byte limit exceeded");
        if (bytes.length >= 3 && (bytes[0]&255)==239 && (bytes[1]&255)==187 && (bytes[2]&255)==191)
            fail("$", "UTF-8 BOM forbidden");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            Object tree = READER.readValue(text, Object.class);
            checkDepth(tree, 0);
            return tree;
        } catch (ContentContractException e) { throw e; }
        catch (Exception e) { throw new ContentContractException("$", "invalid JSON or UTF-8", e); }
    }

    public byte[] bytes(Object value) {
        Output out = new Output();
        append(value, out, 0);
        out.append('\n');
        return out.bytes();
    }

    private static void checkDepth(Object value, int depth) {
        if (depth > 128) fail("$", "JSON depth exceeds 128");
        if (value instanceof String s) wellFormed(s,"$string");
        else if (value instanceof Map<?,?> m) { m.keySet().forEach(k -> wellFormed((String)k,"$key")); m.values().forEach(v -> checkDepth(v, depth+1)); }
        else if (value instanceof List<?> l) l.forEach(v -> checkDepth(v, depth+1));
    }

    private static void append(Object v, Output out, int depth) {
        if (depth > 128) fail("$", "JSON depth exceeds 128");
        if (v == null) { out.append("null"); return; }
        if (v instanceof String s) { quote(s,out); return; }
        if (v instanceof Boolean b) { out.append(b); return; }
        if (v instanceof Number n) { out.append(integer(n, "$number")); return; }
        if (v instanceof Map<?,?> m) {
            var keys = new ArrayList<String>();
            for (Object key : m.keySet()) {
                if (!(key instanceof String)) fail("$", "object key must be a string");
                keys.add((String)key);
            }
            // JSON.stringify enumerates array-index keys numerically, even after canonical sorting.
            keys.sort((a,b) -> {
                Long ia=index(a), ib=index(b);
                if(ia!=null && ib!=null) return Long.compare(ia,ib);
                if(ia!=null) return -1; if(ib!=null) return 1;
                return a.compareTo(b);
            });
            out.append('{');
            for(int i=0;i<keys.size();i++) {
                if(i>0) out.append(',');
                newline(out,depth+1); quote(keys.get(i),out); out.append(": ");
                append(m.get(keys.get(i)),out,depth+1);
            }
            if(!keys.isEmpty()) newline(out,depth);
            out.append('}'); return;
        }
        if(v instanceof List<?> l) {
            out.append('[');
            for(int i=0;i<l.size();i++) {
                if(i>0) out.append(',');
                newline(out,depth+1); append(l.get(i),out,depth+1);
            }
            if(!l.isEmpty())newline(out,depth);
            out.append(']'); return;
        }
        fail("$","unsupported JSON value; use explicit public projections");
    }
    private static Long index(String key) {
        if(!key.matches("0|[1-9][0-9]{0,9}"))return null;
        long n=Long.parseLong(key);return n<4294967295L?n:null;
    }
    private static void newline(Output out,int depth) { out.append('\n').append("  ".repeat(depth)); }
    private static void quote(String s,Output out) {
        wellFormed(s,"$string");
        out.append('"');
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                    else if(Character.isHighSurrogate(c))out.codePoint(Character.toCodePoint(c,s.charAt(++i)));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
    public static long integer(Object value,String path) {
        if(!(value instanceof Number)) throw new ContentContractException(path,"expected safe integer");
        try {
            Number n=(Number)value;
            if(n instanceof Double d && !Double.isFinite(d) || n instanceof Float f && !Float.isFinite(f))
                throw new ArithmeticException();
            BigDecimal decimal=new BigDecimal(n.toString());
            // Bound magnitude before conversion: a short token such as 1e1000000000 must not allocate an enormous BigInteger.
            if(decimal.abs().compareTo(BigDecimal.valueOf(MAX_SAFE_INTEGER))>0)throw new ArithmeticException();
            return decimal.longValueExact();
        } catch(ArithmeticException|NumberFormatException e) { throw new ContentContractException(path,"expected safe integer",e); }
    }
    public static void wellFormed(String s,String path) {
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(++i)))fail(path,"unpaired surrogate");
            } else if(Character.isLowSurrogate(c))fail(path,"unpaired surrogate");
        }
    }
    /** Bounded UTF-8 sink: reject oversized JSON while writing, before allocating a giant intermediate string. */
    private static final class Output {
        private final java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream(1024);
        Output append(char c) { codePoint(c);return this; }
        Output append(long n) { return append(Long.toString(n)); }
        Output append(boolean value) { return append(Boolean.toString(value)); }
        Output append(String s) {
            for(int i=0;i<s.length();) { int c=s.codePointAt(i);codePoint(c);i+=Character.charCount(c); }
            return this;
        }
        void codePoint(int c) {
            int size=c<0x80?1:c<0x800?2:c<0x10000?3:4;
            if(buffer.size()>MAX_BYTES-size)fail("$","byte limit exceeded");
            if(size==1)buffer.write(c);
            else if(size==2) {buffer.write(0xc0|(c>>6));buffer.write(0x80|(c&63));}
            else if(size==3) {buffer.write(0xe0|(c>>12));buffer.write(0x80|((c>>6)&63));buffer.write(0x80|(c&63));}
            else {buffer.write(0xf0|(c>>18));buffer.write(0x80|((c>>12)&63));buffer.write(0x80|((c>>6)&63));buffer.write(0x80|(c&63));}
        }
        byte[] bytes() { return buffer.toByteArray(); }
    }
    private static void fail(String path,String message) { throw new ContentContractException(path,message); }
}
