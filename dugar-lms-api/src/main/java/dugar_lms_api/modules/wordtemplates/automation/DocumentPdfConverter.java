package dugar_lms_api.modules.wordtemplates.automation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.*;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/** LibreOffice performs layout conversion in a separate bounded process and isolated user profile. */
@Service
public class DocumentPdfConverter {
    private final String executable; private final int timeout;
    public DocumentPdfConverter(@Value("${app.document-pdf.executable:soffice}") String executable,
            @Value("${app.document-pdf.timeout-seconds:120}") int timeout) {
        if(executable==null||executable.isBlank()||timeout<1||timeout>600)throw new IllegalArgumentException("Configure a PDF executable and timeout between 1 and 600 seconds");
        this.executable=executable;this.timeout=timeout;
    }
    protected Process start(ProcessBuilder builder) throws IOException {return builder.start();}
    public Path convert(Path docx) {
        Path work=null; Process process=null;
        try {
            work=Files.createTempDirectory(docx.toAbsolutePath().getParent(),"dugar-pdf-");
            Files.copy(docx,work.resolve("original.docx"));
            var builder=new ProcessBuilder(executable,"-env:UserInstallation="+work.resolve("profile").toUri(),"--headless","--convert-to","pdf:writer_pdf_Export","--outdir",work.toString(),work.resolve("original.docx").toString());
            builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process=start(builder);
            if(!process.waitFor(timeout,TimeUnit.SECONDS)) throw new IllegalArgumentException("PDF conversion timed out");
            Path pdf=work.resolve("original.pdf");
            if(process.exitValue()!=0||!Files.isRegularFile(pdf)||Files.size(pdf)<8)throw new IllegalArgumentException("PDF conversion failed; verify LibreOffice and required fonts");
            try(var input=Files.newInputStream(pdf)) {if(!new String(input.readNBytes(5),java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalArgumentException("Converter returned an invalid PDF");}
            Path output=docx.toAbsolutePath().getParent().resolve("dugar-document-"+java.util.UUID.randomUUID()+".pdf");
            Files.move(pdf,output);return output;
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalArgumentException("PDF conversion interrupted");}
        catch(IOException e) {throw new IllegalArgumentException("PDF conversion unavailable; configure app.document-pdf.executable and install LibreOffice");}
        finally {
            if(process!=null&&process.isAlive()){
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();try{process.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
            deleteTree(work);
        }
    }
    static boolean deleteTree(Path root) {
        if(root==null||!Files.exists(root))return true;
        try(var files=Files.walk(root)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);return true;}catch(IOException ignored){return false;}
    }
}
