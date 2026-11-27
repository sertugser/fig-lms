package com.figlms.assignment.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Site settings of the assignment module ({@code fig.assignment.*}); defaults are Moodle 5.3.1's.
 *
 * @param fileSubmissions       the file submission plugin is enabled on the site (REQ-ASG-05: off hides it in every
 *                              assignment, whatever the assignment says)
 * @param onlineTextSubmissions the online text plugin is enabled on the site
 * @param submissionStatement   statement students accept before submitting when the assignment asks for it
 *                              (Moodle {@code assign/submissionstatement}); empty = nothing to accept (REQ-ASG-04)
 */
@ConfigurationProperties("fig.assignment")
record AssignmentProperties(@DefaultValue("true") boolean fileSubmissions,
        @DefaultValue("true") boolean onlineTextSubmissions,
        @DefaultValue("This submission is my own work, except where I have acknowledged the use of the works of other people.")
        String submissionStatement) {
}
