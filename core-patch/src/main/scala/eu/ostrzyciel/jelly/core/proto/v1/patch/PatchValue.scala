package eu.ostrzyciel.jelly.core.proto.v1.patch

import eu.ostrzyciel.jelly.core.proto.v1.RdfValue

/**
 * Trait for possible row values in RDF-Patch that are exclusive to RDF-Patch.
 *
 * This does not include getter and is* methods, as they would not work properly with the typing
 * system. Instead, in Jelly-Patch, we use .asInstanceOf[T] to get instance of the type we want,
 * and we use row type numbers to distinguish between them.
 */
private[core] trait PatchValue extends RdfValue

/**
 * Trait for all possible row values in RDF-Patch.
 */
private[core] type RdfPatchRowValue = PatchValue | RdfValue
