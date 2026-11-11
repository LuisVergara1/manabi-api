package dev.luisvergara.manabi.content.publication.contract;

/** Invalid public content. Deliberately independent of HTTP, persistence and editorial state. */
public final class ContentContractException extends IllegalArgumentException {
    public ContentContractException(String path, String message) { super(path + ": " + message); }
    public ContentContractException(String path, String message, Throwable cause) { super(path + ": " + message, cause); }
}
