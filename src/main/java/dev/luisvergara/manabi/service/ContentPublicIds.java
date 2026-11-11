package dev.luisvergara.manabi.service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.util.*;
public final class ContentPublicIds {
 private ContentPublicIds() {}
 public static String create(String prefix,String initialContent) {
  try {
   String seed=Normalizer.normalize(initialContent,Normalizer.Form.NFC).strip()+"\u0000"+UUID.randomUUID();
   return prefix+"-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8))).substring(0,24);
  } catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
}
