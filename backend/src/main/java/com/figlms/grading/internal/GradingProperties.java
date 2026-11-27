package com.figlms.grading.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param defaultAggregation aggregation of a new course's top category. Moodle's default is Natural, which is not
 *                           implemented; Mean is used instead (see grading.md)
 */
@ConfigurationProperties("fig.grading")
record GradingProperties(@DefaultValue("MEAN") Aggregation defaultAggregation) {
}
