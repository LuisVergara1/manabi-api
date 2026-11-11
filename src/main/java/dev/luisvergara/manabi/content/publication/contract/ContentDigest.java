package dev.luisvergara.manabi.content.publication.contract;
import java.security.*;
import java.util.HexFormat;
public final class ContentDigest {
    private ContentDigest() {}
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
