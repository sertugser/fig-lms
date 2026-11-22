/**
 * Identity & access: login, roles (student, teacher, admin), course-level permissions.
 *
 * <p>Other modules may only use the {@code api} package. Everything else (for example
 * {@code internal}) is private to this module; {@code ModularityTest} fails the build otherwise.
 */
@org.springframework.modulith.ApplicationModule(displayName = "identity")
package com.figlms.identity;
