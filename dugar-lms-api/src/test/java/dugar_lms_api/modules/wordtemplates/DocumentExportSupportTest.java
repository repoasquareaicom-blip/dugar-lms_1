package dugar_lms_api.modules.wordtemplates.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentExportSupportTest {
    @TempDir Path temp;
    @Test void artifactLimitIsEnforcedDuringWritesIncludingZipWrites() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var output=DocumentExportService.boundedOutput(bytes,5)) {
            output.write(new byte[]{1,2,3});output.write(4);
            assertThatThrownBy(() -> output.write(new byte[]{5,6})).isInstanceOf(IOException.class);
            output.write(5);assertThatThrownBy(() -> output.write(6)).isInstanceOf(IOException.class);
        }
        assertThat(bytes.size()).isEqualTo(5);
    }
    @Test void filenamesCannotEscapeZipOrUseWindowsDeviceNames() {
        assertThat(DocumentExportService.safeName("../a\\b:\r\nx")).doesNotContain("/","\\",":","\r","\n").doesNotStartWith(".");
        assertThat(DocumentExportService.safeName("CON")).isEqualTo("_CON");
        assertThat(DocumentExportService.safeName("हिंदी दस्तावेज़")).isEqualTo("हिंदी दस्तावेज़");
        assertThat(DocumentExportService.csv("\t=HYPERLINK(\"x\")")).startsWith("\"'\t=").contains("\"\"x\"\"");
    }
    @Test void pdfUsesIsolatedProfileValidatesSignatureAndCleansWorkDirectory() throws Exception {
        Path docx=Files.writeString(temp.resolve("master.docx"),"fake input");
        var work=new AtomicReference<Path>();var process=mock(Process.class);
        when(process.waitFor(3,TimeUnit.SECONDS)).thenReturn(true);when(process.exitValue()).thenReturn(0);
        var converter=new DocumentPdfConverter("configured-soffice",3) {
            @Override protected Process start(ProcessBuilder builder)throws IOException {
                assertThat(builder.command()).contains("--headless","pdf:writer_pdf_Export");
                Path directory=Path.of(builder.command().get(builder.command().indexOf("--outdir")+1));work.set(directory);
                assertThat(builder.command()).contains("-env:UserInstallation="+directory.resolve("profile").toUri());
                Files.writeString(directory.resolve("original.pdf"),"%PDF-1.7\nconverted");return process;
            }
        };
        Path output=converter.convert(docx);
        try {assertThat(Files.readString(output)).startsWith("%PDF-");assertThat(Files.exists(work.get())).isFalse();assertThat(Files.readString(docx)).isEqualTo("fake input");}
        finally {Files.deleteIfExists(output);}
    }
    @Test void timedOutConverterIsKilledAndPartialFilesAreRemoved() throws Exception {
        Path docx=Files.writeString(temp.resolve("master.docx"),"input");var process=mock(Process.class);
        when(process.isAlive()).thenReturn(true);when(process.descendants()).thenReturn(java.util.stream.Stream.empty());
        var work=new AtomicReference<Path>();
        var converter=new DocumentPdfConverter("soffice",1) {
            @Override protected Process start(ProcessBuilder builder) {
                work.set(Path.of(builder.command().get(builder.command().indexOf("--outdir")+1)));return process;
            }
        };
        assertThatThrownBy(() -> converter.convert(docx)).hasMessageContaining("timed out");
        verify(process).destroyForcibly();assertThat(Files.exists(work.get())).isFalse();
    }
    @Test void missingConverterFailsClearlyAndInvalidTimeoutIsRejected() throws Exception {
        Path docx=Files.writeString(temp.resolve("master.docx"),"input");
        var converter=new DocumentPdfConverter("soffice",1) {
            @Override protected Process start(ProcessBuilder builder)throws IOException {throw new IOException("missing");}
        };
        assertThatThrownBy(() -> converter.convert(docx)).hasMessageContaining("configure app.document-pdf.executable");
        assertThatThrownBy(() -> new DocumentPdfConverter("soffice",0)).isInstanceOf(IllegalArgumentException.class);
    }
}
