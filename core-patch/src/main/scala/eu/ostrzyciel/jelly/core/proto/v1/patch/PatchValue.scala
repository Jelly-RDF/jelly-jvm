package eu.ostrzyciel.jelly.core.proto.v1.patch

import eu.ostrzyciel.jelly.core.proto.v1.RdfValue

/**
 * Trait for possible row values in RDF-Patch that are exclusive to RDF-Patch.
 */
private[core] trait PatchValue extends RdfValue

/**
 * Trait for all possible row values in RDF-Patch.
 */
private[core] type RdfPatchRowValue = PatchValue | RdfValue
