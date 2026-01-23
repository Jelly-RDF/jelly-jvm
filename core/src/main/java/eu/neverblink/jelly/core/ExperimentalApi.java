package eu.neverblink.jelly.core;

import java.lang.annotation.*;

/**
 * Indicates that the annotated element is part of an experimental API and may be subject to change in future releases.
 * <p>
 * This annotation is intended for use by developers who are working with APIs that are not yet stable or finalized.
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
public @interface ExperimentalApi {}
