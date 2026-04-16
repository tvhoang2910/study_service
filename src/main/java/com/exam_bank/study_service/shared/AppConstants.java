package com.exam_bank.study_service.shared;

import java.time.ZoneId;

/**
 * Application-wide constants shared across features.
 *
 * <p><strong>IMPORTANT:</strong> The timezone string {@code "Asia/Ho_Chi_Minh"} is also
 * hardcoded in native SQL queries ({@code AT TIME ZONE 'Asia/Ho_Chi_Minh'}) inside
 * repository interfaces. If you change this value, update those queries as well.
 */
public final class AppConstants {

    private AppConstants() {
    }

    /** Application timezone used for date calculations and SQL conversions. */
    public static final String APP_TIMEZONE = "Asia/Ho_Chi_Minh";

    /** Pre-built {@link ZoneId} for {@link #APP_TIMEZONE}. */
    public static final ZoneId APP_ZONE = ZoneId.of(APP_TIMEZONE);
}
