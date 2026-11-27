package com.figlms.files.internal;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the files module ({@code fig.files.*}).
 *
 * @param root     directory of the file contents (Moodle {@code $CFG->filedir})
 * @param maxBytes site upload limit (Moodle {@code maxbytes}); the servlet multipart limit in application.yml must not
 *                 be lower
 */
@ConfigurationProperties("fig.files")
record FilesProperties(@DefaultValue("./data/files") Path root, @DefaultValue("20MB") DataSize maxBytes) {
}
