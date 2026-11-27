/**
 * Files: content-addressed storage of uploaded files, file names, upload limits and protected download.
 *
 * <p>Other modules may only use the {@code api} package. Everything else (for example
 * {@code internal}) is private to this module; {@code ModularityTest} fails the build otherwise.
 */
@org.springframework.modulith.ApplicationModule(displayName = "files")
package com.figlms.files;
