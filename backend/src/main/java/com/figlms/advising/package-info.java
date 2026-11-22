/**
 * Advising: course prerequisite check (MDL-22648), advisor approval, credit limit.
 *
 * <p>Other modules may only use the {@code api} package. Everything else (for example
 * {@code internal}) is private to this module; {@code ModularityTest} fails the build otherwise.
 */
@org.springframework.modulith.ApplicationModule(displayName = "advising")
package com.figlms.advising;
