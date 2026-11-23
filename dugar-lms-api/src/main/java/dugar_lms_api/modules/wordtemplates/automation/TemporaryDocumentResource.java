package dugar_lms_api.modules.wordtemplates.automation;

import org.springframework.core.io.FileSystemResource;
import java.io.*;
import java.nio.file.*;

/** Delete temporary output when Spring closes the download stream. */
public final class TemporaryDocumentResource extends FileSystemResource {
    private final Path output;
    public TemporaryDocumentResource(Path output) {super(output);this.output=output;}
    @Override public InputStream getInputStream() throws IOException {
        return new FilterInputStream(super.getInputStream()) {
            @Override public void close() throws IOException {
                try {super.close();} finally {Files.deleteIfExists(output);}
            }
        };
    }
}
