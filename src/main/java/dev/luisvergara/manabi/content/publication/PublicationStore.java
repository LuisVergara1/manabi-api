package dev.luisvergara.manabi.content.publication;
import java.io.*;
import java.nio.file.Path;
import dev.luisvergara.manabi.content.publication.contract.PublicProtocol.Descriptor;
/** Internal storage. Official authorization/visibility belong to the ledger and services. */
public interface PublicationStore {
 record ExportFile(String key,String sha256,long byteLength) {}
 void putPackage(Descriptor d,byte[] bytes)throws IOException;
 void putManifest(String releaseId,byte[] bytes)throws IOException;
 byte[] readPackage(Descriptor d)throws IOException;
 byte[] readManifest(String releaseId)throws IOException;
 Path stage()throws IOException;
 ExportFile completeExport(String releaseId,Path staged)throws IOException;
 InputStream openExport(ExportFile file)throws IOException;
 void discardStage(Path staged)throws IOException;
}
