package dev.luisvergara.manabi.content.publication.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static dev.luisvergara.manabi.content.publication.contract.PublicFixtures.*;

class CanonicalJsonTest {
    @Test void allApprovedVectorsMatchExactBytesLengthHashAndReleasePreimage() {
        var vectors=tree("json-conformance-v1.json");
        for(var v:rows(vectors.get("canonical"))) {
            byte[] expected=Base64.getDecoder().decode(s(v,"utf8Base64")), actual=JSON.bytes(v.get("input"));
            assertArrayEquals(expected,actual,s(v,"id"));
            assertEquals(number(v.get("byteLength")),actual.length);
            assertEquals(s(v,"sha256"),ContentDigest.sha256(actual));
        }
        assertEquals("f880c41a8669ea0fc11fa23efe482f8ceb843653142384cce7f7effc7920eae9",
            ContentDigest.sha256(resource("json-conformance-v1.json")));
    }
    @Test void javaUtf16AndJavascriptArrayIndexKeyEnumerationArePreserved() {
        var m=new HashMap<String,Object>();
        for(String key:List.of("10","2","1","01","4294967295","4294967294"))m.put(key,0);
        String result=new String(JSON.bytes(m),StandardCharsets.UTF_8);
        assertEquals("{\n  \"1\": 0,\n  \"2\": 0,\n  \"10\": 0,\n  \"4294967294\": 0,\n  \"01\": 0,\n  \"4294967295\": 0\n}\n",result);
        String astral=new String(Character.toChars(0x10000)),bmp="\ue000";
        result=new String(JSON.bytes(Map.of(bmp,1,astral,2)),StandardCharsets.UTF_8);
        assertTrue(result.indexOf(astral)<result.indexOf(bmp));
    }
    @Test void stringEscapingRetainsUnicodeSlashSeparatorsAndSingleFinalLf() {
        String value="あ/\"\\\b\f\n\r\t"+(char)0+"\u2028\u2029"+new String(Character.toChars(0x20000));
        String result=new String(JSON.bytes(value),StandardCharsets.UTF_8);
        assertTrue(result.contains("あ/"));assertFalse(result.contains("\\/"));assertTrue(result.contains("\\u0000"));
        assertTrue(result.contains("\u2028\u2029"));assertFalse(result.contains("\\u2028"));
        assertTrue(result.endsWith("\"\n"));assertFalse(result.contains("\r\n"));
        assertEquals(value,JSON.parse(JSON.bytes(value)));
    }
    @Test void strictParsingRejectsDuplicateDecodedKeysTrailingTokensBomAndInvalidUtf8() {
        for(String raw:List.of("{\"a\":1,\"a\":2}","{\"a\":1,\"\\u0061\":2}","{}{}","{\"a\":}","[NaN]","{\"a\":1,}","{\"a\":\"\\ud800\"}"))
            assertThrows(ContentContractException.class,()->JSON.parse(raw.getBytes(StandardCharsets.UTF_8)),raw);
        assertThrows(ContentContractException.class,()->JSON.parse(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,123,125}));
        assertThrows(ContentContractException.class,()->JSON.parse(new byte[]{(byte)0xc3,40}));
    }
    @Test void depthAndByteBudgetsAreExplicit() {
        assertDoesNotThrow(()->JSON.parse(("[".repeat(129)+"]".repeat(129)).getBytes(StandardCharsets.UTF_8)));
        assertThrows(ContentContractException.class,()->JSON.parse(("[".repeat(130)+"]".repeat(130)).getBytes(StandardCharsets.UTF_8)));
        assertThrows(ContentContractException.class,()->JSON.parse(new byte[CanonicalJson.MAX_BYTES+1]));
        assertThrows(ContentContractException.class,()->JSON.bytes("x".repeat(CanonicalJson.MAX_BYTES)));
    }
    @Test void integersMatchPublicNumericDomainWithoutLocaleFractionsOrNonFiniteValues() {
        assertEquals("9007199254740991\n",new String(JSON.bytes(9007199254740991L),StandardCharsets.UTF_8));
        assertEquals("0\n",new String(JSON.bytes(-0.0d),StandardCharsets.UTF_8));
        assertEquals("1\n",new String(JSON.bytes(new java.math.BigDecimal("1e0")),StandardCharsets.UTF_8));
        for(Number value:List.<Number>of(9007199254740992L,Double.NaN,Double.POSITIVE_INFINITY,1.25d))
            assertThrows(ContentContractException.class,()->JSON.bytes(value));
    }
    @Test void arbitraryEntitiesSetsAndNonStringKeysAreNeverReflectedOrReordered() {
        assertThrows(ContentContractException.class,()->JSON.bytes(new dev.luisvergara.manabi.entity.kana.Kana()));
        assertThrows(ContentContractException.class,()->JSON.bytes(Set.of("b","a")));
        assertThrows(ContentContractException.class,()->JSON.bytes(Map.of(1,"numeric-key")));
    }
    @Test void hugeExponentCannotBypassNumericBudgetOrAllocateHugeInteger() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            for (String value : List.of("1e1000000000", "1e-1000000000")) {
                Object number=JSON.parse(value.getBytes(StandardCharsets.UTF_8));
                assertThrows(ContentContractException.class, () -> JSON.bytes(number));
            }
        });
    }
    @Test void utf8BudgetStopsBeforeReadingLaterValues() {
        var input=new LinkedHashMap<String,Object>();
        input.put("a","x".repeat(CanonicalJson.MAX_BYTES));input.put("z",new Object());
        var failure=assertThrows(ContentContractException.class,()->JSON.bytes(input));
        assertTrue(failure.getMessage().contains("byte limit exceeded"));
    }
}
