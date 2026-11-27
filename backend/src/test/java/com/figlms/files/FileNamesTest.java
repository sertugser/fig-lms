package com.figlms.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.figlms.files.api.FileNames;
import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-FIL-04: Moodle PARAM_FILE cleaning, and accepted file types. */
class FileNamesTest {

    @Test
    void normal_safeNameIsKept() {
        assertThat(FileNames.clean("report final (2).pdf")).isEqualTo("report final (2).pdf");
    }

    @Test
    void invalid_unsafeCharactersAreRemoved() {
        assertThat(FileNames.clean("../../etc/passwd")).isEqualTo("....etcpasswd");
        assertThat(FileNames.clean("a<b>c&d\"e`f|g'h:i\\j/k\u0007.txt")).isEqualTo("abcdefghijk.txt");
    }

    @Test
    void boundary_dotNamesBecomeEmpty() {
        assertThat(FileNames.clean(".")).isEmpty();
        assertThat(FileNames.clean("..")).isEmpty();
        assertThat(FileNames.clean("/")).isEmpty();
        assertThat(FileNames.clean(null)).isEmpty();
    }

    @Test
    void normal_acceptedTypes() {
        assertThat(FileNames.hasAcceptedType("Report.PDF", List.of(".pdf", "docx"))).isTrue();
        assertThat(FileNames.hasAcceptedType("report.exe", List.of(".pdf"))).isFalse();
        assertThat(FileNames.hasAcceptedType(".pdf", List.of(".pdf"))).isFalse();
        assertThat(FileNames.hasAcceptedType("anything.bin", List.of())).isTrue();
    }
}
