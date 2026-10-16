package dugar_lms_api.modules.wordtemplates.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipFile;

@Service
public class WordTemplateStorageService {

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final Path storageRoot;
    private final long maxFileSizeBytes;

    public WordTemplateStorageService(
        @Value("${app.word-templates.storage-dir:${WORD_TEMPLATE_STORAGE_DIR:./storage/word-templates}}") String storageDir,
        @Value("${app.word-templates.max-file-size-bytes:10485760}") long maxFileSizeBytes
    ) {
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public StoredTemplateFile store(MultipartFile file) {
        validateUpload(file);
        try {
            Files.createDirectories(storageRoot);
            String storedFilename = UUID.randomUUID() + ".docx";
            Path target = resolveStoredFilename(storedFilename);
            file.transferTo(target);
            validateDocxStructure(target);
            return new StoredTemplateFile(cleanOriginalFilename(file.getOriginalFilename()), storedFilename);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to store DOCX template.");
        }
    }

    public UrlResource resource(String storedFilename) {
        try {
            Path path = resolveStoredFilename(storedFilename);
            UrlResource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Template file is missing from storage.");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Template file path is invalid.");
        }
    }

    public Path path(String storedFilename) {
        return resolveStoredFilename(storedFilename);
    }

    public void deleteQuietly(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredFilename(storedFilename));
        } catch (IOException ignored) {
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("DOCX file is required.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("DOCX file exceeds the configured size limit.");
        }
        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("Only .docx files are allowed.");
        }
        String contentType = file.getContentType();
        if (contentType != null
            && !contentType.isBlank()
            && !DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType)
            && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Invalid DOCX content type.");
        }
    }

    private void validateDocxStructure(Path path) {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            if (zipFile.getEntry("[Content_Types].xml") == null || zipFile.getEntry("word/document.xml") == null) {
                deleteQuietly(path.getFileName().toString());
                throw new IllegalArgumentException("Invalid DOCX file structure.");
            }
        } catch (IOException exception) {
            deleteQuietly(path.getFileName().toString());
            throw new IllegalArgumentException("Invalid DOCX file structure.");
        }
    }

    private Path resolveStoredFilename(String storedFilename) {
        String filename = Paths.get(String.valueOf(storedFilename)).getFileName().toString();
        Path resolved = storageRoot.resolve(filename).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Template file path is invalid.");
        }
        return resolved;
    }

    private String cleanOriginalFilename(String value) {
        String filename = Paths.get(String.valueOf(value == null ? "" : value)).getFileName().toString().trim();
        return filename.isBlank() ? "template.docx" : filename;
    }

    public record StoredTemplateFile(String originalFilename, String storagePath) {
    }
}
