package net.falsetrue.heapwalker.util;

import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class NameUtils {
    private NameUtils() {
    }

    @NotNull
    public static String getUniqueName(@NotNull ObjectReference ref) {
        String shortName = StringUtil.getShortName(ref.referenceType().name());
        String name = shortName.replace("[]", "Array");
        return String.format("%s@%d", name, ref.uniqueID());
    }

    @NotNull
    public static String getArrayUniqueName(@NotNull ArrayReference ref) {
        String shortName = StringUtil.getShortName(ref.referenceType().name());
        int length = ref.length();
        String name = shortName.replaceFirst(Pattern.quote("[]"), String.format("[%d]", length));
        return String.format("%s@%d", name, ref.uniqueID());
    }

    public static String locationToString(Location location) {
        if (location.method().name().isEmpty()) {
            try {
                return location.sourceName() + ":" + location.lineNumber();
            } catch (AbsentInformationException e) {
                return location.toString();
            }
        }
        return String.format("%s:%d, %s", location.method().name(), location.lineNumber(),
            location.declaringType().name());
    }

    public static String minsSecs(long milliseconds) {
        if (milliseconds == 0) {
            return "just now";
        }
        StringBuilder builder = new StringBuilder();
        if (milliseconds >= 60000) {
            builder.append(milliseconds / 60000).append(" min");
            if (milliseconds / 1000 % 60 != 0) {
                builder.append(" ");
            }
        }
        if (milliseconds / 1000 % 60 != 0) {
            builder.append(milliseconds / 1000 % 60).append(" sec");
            if (milliseconds < 3000 && milliseconds % 1000 != 0) {
                builder.append(" ");
            }
        }
        if (milliseconds < 3000 && milliseconds % 1000 != 0) {
            builder.append(milliseconds % 1000).append(" ms");
        }
        return builder.toString();
    }
}
