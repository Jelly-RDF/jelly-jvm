package eu.neverblink.jelly.core;

import java.lang.annotation.*;

/**
 * Indicates that the annotated element is part of an internal API and should not be used outside of the Jelly-JVM.
 * <p>
 * This annotation is intended for use by developers who are working with APIs that are not intended for public use.
 * <p>
 * Note: This annotation is not intended for public use and should only be used internally within the Jelly-JVM.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(
    {
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PACKAGE,
        ElementType.TYPE,
    }
)
@Documented
public @interface InternalApi {}
