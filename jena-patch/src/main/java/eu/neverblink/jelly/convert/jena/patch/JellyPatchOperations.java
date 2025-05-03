package eu.neverblink.jelly.convert.jena.patch;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.patch.PatchHandler;
import eu.neverblink.jelly.core.proto.v1.PatchStatementType;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFChanges;

/**
 * Jelly-based operations on RDFChanges streams and RDFPatch objects from Jena.
 */
@ExperimentalApi
public class JellyPatchOperations {

    private JellyPatchOperations() {}

    /**
     * Convert a Jelly-Patch stream to a Jena RDFChanges stream.
     * @param destination The RDFChanges stream to write to.
     * @return A Jelly-Patch stream handler that relays all operations to the Jena RDFChanges stream.
     */
    public static PatchHandler.AnyPatchHandler<Node> fromJellyToJena(RDFChanges destination) {
        return new JellyToJenaPatchHandler(destination);
    }

    /**
     * Convert a Jena RDFChanges stream to a Jelly-Patch stream.
     * <p>
     * This variant does not specify the statement type. "null" in the graph position will be treated
     * as a triple (no information about the graph), and "urn:x-arq:DefaultGraphNode" or
     * "urn:x-arq:DefaultGraph" will be treated as a quad (triple in the default graph).
     * <p>
     * If you want to force the statements to be interpreted as triples or quads, use
     * `fromJenaToJellyTriples` or `fromJenaToJellyQuads` instead.
     *
     * @param destination The Jelly-Patch stream to write to.
     * @return A Jena RDFChanges instance that relays all operations to the Jelly-Patch stream.
     */
    public static RDFChanges fromJenaToJelly(PatchHandler.AnyPatchHandler<Node> destination) {
        return new JenaToJellyPatchHandler(destination, PatchStatementType.PATCH_STATEMENT_TYPE_UNSPECIFIED);
    }

    /**
     * Convert a Jena RDFChanges stream to a Jelly-Patch stream with the statement type set to TRIPLES.
     * <p>
     * All incoming statements will be treated as triples, regardless of what is specified in the
     * graph term (graph name is discarded).
     *
     * @param jellyStream The Jelly-Patch stream to write to.
     * @return A Jena RDFChanges instance that relays all operations to the Jelly-Patch stream.
     */
    public static RDFChanges fromJenaToJellyTriples(PatchHandler.AnyPatchHandler<Node> jellyStream) {
        return new JenaToJellyPatchHandler(jellyStream, PatchStatementType.PATCH_STATEMENT_TYPE_TRIPLES);
    }

    /**
     * Convert a Jena RDFChanges stream to a Jelly-Patch stream with the statement type set to QUADS.
     * <p>
     * All incoming statements will be treated as quads. If the graph term is null, it will be
     * interpreted as a triple in the default graph.
     *
     * @param jellyStream The Jelly-Patch stream to write to.
     * @return A Jena RDFChanges instance that relays all operations to the Jelly-Patch stream.
     */
    public static RDFChanges fromJenaToJellyQuads(PatchHandler.AnyPatchHandler<Node> jellyStream) {
        return new JenaToJellyPatchHandler(jellyStream, PatchStatementType.PATCH_STATEMENT_TYPE_QUADS);
    }
}
