package com.labmind.business.chat.support;

import com.labmind.common.frame.enums.BaseCode;
import com.labmind.common.frame.exception.BaseException;
import org.springframework.util.StringUtils;

public final class BusinessInputValidator {

    private BusinessInputValidator() {
    }

    public static String normalizeRequiredText(String value, String fieldName) {
        String normalized = value == null ? null : value.strip();
        if (!StringUtils.hasText(normalized)) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must not be blank");
        }
        return normalized;
    }

    public static String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.strip();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    public static int parsePositiveInt(String value, String fieldName) {
        long parsedValue = parsePositiveLong(value, fieldName);
        if (parsedValue > Integer.MAX_VALUE) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " is too large");
        }
        return (int) parsedValue;
    }

    public static long parsePositiveLong(String value, String fieldName) {
        String normalized = normalizeRequiredText(value, fieldName);
        try {
            long parsedValue = Long.parseLong(normalized);
            if (parsedValue <= 0) {
                throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must be a positive integer");
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must be a positive integer");
        }
    }
}
