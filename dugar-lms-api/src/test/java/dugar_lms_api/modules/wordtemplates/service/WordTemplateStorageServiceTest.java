package dugar_lms_api.modules.wordtemplates.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordTemplateStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidDocxWithGeneratedFilename() throws Exception {
        WordTemplateStorageService service = new WordTemplateStorageService(tempDir.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "Original Template.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            validDocx()
        );

        WordTemplateStorageService.StoredTemplateFile stored = service.store(file);

        assertThat(stored.originalFilename()).isEqualTo("Original Template.docx");
        assertThat(stored.storagePath()).endsWith(".docx");
        assertThat(stored.storagePath()).isNotEqualTo("Original Template.docx");
        assertThat(Files.exists(tempDir.resolve(stored.storagePath()))).isTrue();
    }

    @Test
    void rejectsInvalidDocxStructure() {
        WordTemplateStorageService service = new WordTemplateStorageService(tempDir.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "bad.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[] { 1, 2, 3 }
        );

        assertThatThrownBy(() -> service.store(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid DOCX");
    }

    @Test
    void rejectsNonDocxExtension() throws Exception {
        WordTemplateStorageService service = new WordTemplateStorageService(tempDir.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "template.pdf",
            "application/pdf",
            validDocx()
        );

        assertThatThrownBy(() -> service.store(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(".docx");
    }

    private byte[] validDocx() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
