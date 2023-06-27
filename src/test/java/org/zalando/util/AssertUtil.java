package org.zalando.util;

import org.junit.jupiter.api.function.Executable;
import org.junit.platform.commons.util.UnrecoverableExceptions;

import static java.lang.String.format;
import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;

public class AssertUtil {

    public static <T extends Throwable> T assertThrowsWithCause(Class<T> expectedType,
                                                                Executable executable) {

        try {
            executable.execute();
        } catch (Throwable actualException) {
            if (expectedType.isInstance(actualException.getCause())) {
                return (T) actualException.getCause();
            } else {
                UnrecoverableExceptions.rethrowIfUnrecoverable(actualException);
                throw assertionFailure()
                        .expected(expectedType)
                        .actual(actualException.getCause().getClass())
                        .reason("Unexpected exception type thrown")
                        .cause(actualException)
                        .build();
            }
        }
        throw assertionFailure()
                .reason(format("Expected exception with cause %s to be thrown, but nothing was thrown.", expectedType.getName()))
                .build();
    }
}
