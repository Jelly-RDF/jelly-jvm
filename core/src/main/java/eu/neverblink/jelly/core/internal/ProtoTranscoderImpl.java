package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.*;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import eu.neverblink.protoc.java.runtime.RepeatedInt;

/**
 * Transcoder for Jelly-RDF 1.2 streams. Copies the columns, only remapping lookup ids.
 */
@InternalApi
public final class ProtoTranscoderImpl implements ProtoTranscoder {

    private final RdfStreamOptions supportedInputOptions;
    private final RdfStreamOptions outputOptions;
    private final boolean messages;
    private final int outputRdfVersion;

    private final TranscoderLookup names;
    private final TranscoderLookup prefixes;
    private final TranscoderLookup datatypes;

    private boolean seenOptions = false;
    private boolean emittedOptions = false;
    // Rows since the last message boundary (MESSAGES streams)
    private long rowsInMessage = 0;

    /**
     * @param supportedInputOptions maximum allowable options for the input streams (optional)
     * @param outputOptions options for the output stream. This MUST have the physical stream type set.
     */
    public ProtoTranscoderImpl(RdfStreamOptions supportedInputOptions, RdfStreamOptions outputOptions) {
        if (JellyConstants.requestsRowLayout(outputOptions.getVersion())) {
            throw new IllegalArgumentException(
                "The transcoder only writes Jelly-RDF 1.2 streams, but the output options ask for protocol version %d.".formatted(
                    outputOptions.getVersion()
                )
            );
        }
        final PhysicalStreamType physicalType = outputOptions.getPhysicalType();
        if (physicalType != PhysicalStreamType.TRIPLES && physicalType != PhysicalStreamType.QUADS) {
            throw new IllegalArgumentException(
                "The physical type of the output stream must be TRIPLES or QUADS, got: %s".formatted(physicalType)
            );
        }
        this.supportedInputOptions = supportedInputOptions;
        this.outputOptions = outputOptions
            .clone()
            .setVersion(JellyConstants.PROTO_VERSION_1_2_X)
            .setGeneralizedStatements(false)
            .setRdfStar(false)
            .setLogicalType(LogicalStreamType.UNSPECIFIED);
        this.messages = outputOptions.getStreamTypeValue() == RdfStreamType.MESSAGES_VALUE;
        this.outputRdfVersion = outputOptions.getRdfVersionValue();
        names = new TranscoderLookup("name", outputOptions.getMaxNameTableSize());
        prefixes = new TranscoderLookup("prefix", outputOptions.getMaxPrefixTableSize());
        datatypes = new TranscoderLookup("datatype", outputOptions.getMaxDatatypeTableSize());
    }

    @Override
    public RdfStreamFrame ingestFrame(RdfStreamFrame frame) {
        final RdfStreamFrame.Mutable out = RdfStreamFrame.newInstance();
        boolean endMessage = false;
        final var rows = frame.getRows();
        if (!rows.isEmpty()) {
            final RdfStreamRow row = rows.iterator().next();
            if (rows.size() > 1 || row.getRowFieldNumber() != RdfStreamRow.OPTIONS) {
                throw new RdfProtoTranscodingError(
                    "The transcoder only supports Jelly-RDF 1.2 streams (column layout), whose frames have " +
                        "no rows other than the stream options."
                );
            }
            endMessage = handleOptions(row.getOptions());
            if (!emittedOptions) {
                emittedOptions = true;
                out.addRows(RdfStreamRow.newInstance().setOptions(outputOptions));
            }
        }
        final RdfColumnBatch batch = frame.getColumns();
        if (batch != null) {
            if (!seenOptions) {
                throw new RdfProtoTranscodingError("The input stream has no stream options before its first batch.");
            }
            out.setColumns(transcodeBatch(batch, endMessage));
        } else if (endMessage) {
            out.setColumns(RdfColumnBatch.newInstance().addMessageLengths(0));
        }
        out.getMetadata().addAll(frame.getMetadata());
        return out;
    }

    /** @return whether the current output message must end here */
    private boolean handleOptions(RdfStreamOptions options) {
        if (JellyConstants.isRowLayout(options.getVersion())) {
            throw new RdfProtoTranscodingError(
                "The transcoder only supports Jelly-RDF 1.2 streams (column layout), not Jelly-RDF 1.0 or 1.1."
            );
        }
        if (supportedInputOptions != null) {
            JellyOptions.checkCompatibility(options, supportedInputOptions);
        }
        if (options.getPhysicalTypeValue() != outputOptions.getPhysicalTypeValue()) {
            throw new RdfProtoTranscodingError(
                "Input stream has a different physical type than the output. Input: %s output: %s".formatted(
                    options.getPhysicalType(),
                    outputOptions.getPhysicalType()
                )
            );
        }
        if (options.getStreamTypeValue() != outputOptions.getStreamTypeValue()) {
            throw new RdfProtoTranscodingError(
                "Input stream has a different stream type than the output. Input: %s output: %s".formatted(
                    options.getStreamType(),
                    outputOptions.getStreamType()
                )
            );
        }
        checkTableSize(names, options.getMaxNameTableSize());
        checkTableSize(prefixes, options.getMaxPrefixTableSize());
        checkTableSize(datatypes, options.getMaxDatatypeTableSize());
        names.newInputStream(options.getMaxNameTableSize());
        prefixes.newInputStream(options.getMaxPrefixTableSize());
        datatypes.newInputStream(options.getMaxDatatypeTableSize());
        seenOptions = true;
        // New options end the input's message
        if (messages && rowsInMessage > 0) {
            rowsInMessage = 0;
            return true;
        }
        return false;
    }

    private static void checkTableSize(TranscoderLookup lookup, int inputSize) {
        if (inputSize > lookup.outputSize || inputSize < 0) {
            throw new RdfProtoTranscodingError(
                "The input stream's lookup tables must not be larger than the output's, but its %s table has size %s and the output's %d.".formatted(
                    lookup.tableName,
                    Integer.toUnsignedString(inputSize),
                    lookup.outputSize
                )
            );
        }
    }

    private RdfColumnBatch transcodeBatch(RdfColumnBatch in, boolean endMessage) {
        final RdfColumnBatch.Mutable out = RdfColumnBatch.newInstance();
        final int rows = in.getRowCount();
        out.setRowCount(rows);

        names.startBatch(out.getNames());
        prefixes.startBatch(out.getPrefixes());
        datatypes.startBatch(out.getDatatypes());
        names.addInputEntries(in.getNames());
        prefixes.addInputEntries(in.getPrefixes());
        datatypes.addInputEntries(in.getDatatypes());

        if (!in.getNamespaces().isEmpty()) {
            final IriState iris = new IriState();
            for (final RdfNamespaceDeclaration ns : in.getNamespaces()) {
                if (ns.getValue() == null) {
                    throw new RdfProtoTranscodingError(
                        "Namespace declaration '%s' has no IRI.".formatted(ns.getName())
                    );
                }
                out.addNamespaces(
                    RdfNamespaceDeclaration.newInstance().setName(ns.getName()).setValue(iris.remap(ns.getValue()))
                );
            }
        }

        out.setSubjects(transcodeColumn(in.getSubjects()));
        out.setPredicates(transcodeColumn(in.getPredicates()));
        out.setObjects(transcodeColumn(in.getObjects()));
        out.setGraphs(transcodeColumn(in.getGraphs()));

        final RepeatedInt lengths = in.getMessageLengths();
        if (!messages) {
            if (!lengths.isEmpty()) {
                throw new RdfProtoTranscodingError("Message boundaries are only valid in streams of type MESSAGES.");
            }
            return out;
        }
        if (endMessage) {
            out.addMessageLengths(0);
            out.getMessageLengths().addAll(lengths);
        } else {
            out.setMessageLengths(lengths);
        }
        if (lengths.isEmpty()) {
            rowsInMessage += rows;
        } else {
            long sum = 0;
            for (int i = 0; i < lengths.size(); i++) {
                sum += Integer.toUnsignedLong(lengths.get(i));
            }
            rowsInMessage = Integer.toUnsignedLong(rows) - sum;
        }
        return out;
    }

    /** Shares everything with the input column except the remapped ids. */
    private RdfColumn transcodeColumn(RdfColumn in) {
        if (in == null) {
            return null;
        }
        final RdfColumn.Mutable out = RdfColumn.newInstance()
            .setKinds(in.getKinds())
            .setLayouts(in.getLayouts())
            .setLexValues(in.getLexValues())
            .setLangtags(in.getLangtags())
            .setLangtagDirections(in.getLangtagDirections())
            .setBnodes(in.getBnodes());

        final RepeatedInt nameIds = in.getNameIds();
        if (!nameIds.isEmpty()) {
            out.setNameIds(remapNameIds(nameIds));
            out.setPrefixIds(remapPrefixIds(in.getPrefixIds(), nameIds.size()));
        } else if (!in.getPrefixIds().isEmpty()) {
            throw new RdfProtoTranscodingError("Corrupt column: prefix ids without name ids.");
        }
        final RepeatedInt literalKinds = in.getLiteralKinds();
        if (!literalKinds.isEmpty()) {
            out.setLiteralKinds(remapLiteralKinds(literalKinds));
        }
        if (outputRdfVersion == RdfVersion.RDF_VERSION_1_1_VALUE) {
            final RepeatedInt directions = in.getLangtagDirections();
            for (int i = 0; i < directions.size(); i++) {
                if (directions.get(i) != RdfBaseDirection.UNSPECIFIED_VALUE) {
                    throw notInOutputVersion("base directions");
                }
            }
        }
        final MessageCollection<RdfTripleTerm, RdfTripleTerm.Mutable> tripleTerms = in.getTripleTerms();
        if (!tripleTerms.isEmpty()) {
            if (
                outputRdfVersion != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE &&
                outputRdfVersion < RdfVersion.RDF_VERSION_1_2_VALUE
            ) {
                throw notInOutputVersion("triple terms");
            }
            final IriState iris = new IriState();
            final MessageCollection<RdfTripleTerm, RdfTripleTerm.Mutable> outTerms = out.getTripleTerms();
            for (final RdfTripleTerm term : tripleTerms) {
                outTerms.add(remapTripleTerm(term, iris));
            }
        }
        return out;
    }

    private RdfProtoTranscodingError notInOutputVersion(String what) {
        return new RdfProtoTranscodingError(
            "The input stream contains %s, but the output stream declares %s.".formatted(
                what,
                RdfVersionUtils.rdfVersionName(outputRdfVersion)
            )
        );
    }

    // 0 = previous id + 1
    private RepeatedInt remapNameIds(RepeatedInt in) {
        final int n = in.size();
        final int[] values = in.array();
        final int[] out = new int[n];
        int lastIn = 0;
        int lastOut = 0;
        for (int i = 0; i < n; i++) {
            final int v = values[i];
            final int id = v == 0 ? lastIn + 1 : v;
            lastIn = id;
            final int o = names.remap(id);
            out[i] = o == lastOut + 1 ? 0 : o;
            lastOut = o;
        }
        final RepeatedInt result = RepeatedInt.newEmptyInstance();
        result.wrap(out, n);
        return result;
    }

    // 0, 1 or one per IRI; 0 = same prefix as before
    private RepeatedInt remapPrefixIds(RepeatedInt in, int iriCount) {
        final int n = in.size();
        if (n == 0) {
            return in;
        }
        if (n != 1 && n != iriCount) {
            throw new RdfProtoTranscodingError(
                "Corrupt column: %d prefix ids for %d name ids, expected 0, 1 or %d.".formatted(n, iriCount, iriCount)
            );
        }
        final int[] values = in.array();
        final int[] out = new int[n];
        int lastIn = 0;
        int lastOut = 0;
        for (int i = 0; i < n; i++) {
            final int v = values[i];
            if (v == 0 || v == lastIn) {
                out[i] = 0;
                continue;
            }
            lastIn = v;
            final int o = prefixes.remap(v);
            out[i] = o == lastOut ? 0 : o;
            lastOut = o;
        }
        final RepeatedInt result = RepeatedInt.newEmptyInstance();
        result.wrap(out, n);
        return result;
    }

    // Odd kind v = datatype (v + 1) / 2
    private RepeatedInt remapLiteralKinds(RepeatedInt in) {
        final int n = in.size();
        final int[] values = in.array();
        final int[] out = new int[n];
        int lastIn = 0;
        int lastOut = 0;
        for (int i = 0; i < n; i++) {
            final int v = values[i];
            if ((v & 1) == 0) {
                out[i] = v;
            } else if (v == lastIn) {
                out[i] = lastOut;
            } else {
                lastIn = v;
                lastOut = datatypes.remap((v >>> 1) + 1) * 2 - 1;
                out[i] = lastOut;
            }
        }
        final RepeatedInt result = RepeatedInt.newEmptyInstance();
        result.wrap(out, n);
        return result;
    }

    private RdfTripleTerm remapTripleTerm(RdfTripleTerm in, IriState iris) {
        final RdfTripleTerm.Mutable out = RdfTripleTerm.newInstance();
        switch (in.getSubjectFieldNumber()) {
            case RdfTripleTerm.S_IRI -> out.setSIri(iris.remap(in.getSIri()));
            case RdfTripleTerm.S_BNODE -> out.setSBnode(in.getSBnode());
            default -> throw new RdfProtoTranscodingError("A triple term has no subject.");
        }
        if (in.getPIri() == null) {
            throw new RdfProtoTranscodingError("A triple term has no predicate.");
        }
        out.setPIri(iris.remap(in.getPIri()));
        switch (in.getObjectFieldNumber()) {
            case RdfTripleTerm.O_IRI -> out.setOIri(iris.remap(in.getOIri()));
            case RdfTripleTerm.O_BNODE -> out.setOBnode(in.getOBnode());
            case RdfTripleTerm.O_LITERAL -> out.setOLiteral(remapLiteral(in.getOLiteral()));
            case RdfTripleTerm.O_TRIPLE_TERM -> out.setOTripleTerm(remapTripleTerm(in.getOTripleTerm(), iris));
            default -> throw new RdfProtoTranscodingError("A triple term has no object.");
        }
        return out;
    }

    private RdfLiteral remapLiteral(RdfLiteral in) {
        if (
            outputRdfVersion == RdfVersion.RDF_VERSION_1_1_VALUE &&
            in.getDirectionValue() != RdfBaseDirection.UNSPECIFIED_VALUE
        ) {
            throw notInOutputVersion("base directions");
        }
        if (!in.hasDatatype()) {
            return in;
        }
        return RdfLiteral.newInstance()
            .setLex(in.getLex())
            .setDatatype(datatypes.remap(in.getDatatype()))
            .setDirectionValue(in.getDirectionValue());
    }

    /** Remaps RdfIri messages (namespaces, triple terms), keeping their 0-id inference. */
    private final class IriState {

        private int lastInPrefix = 0;
        private int lastInName = 0;
        private int lastOutPrefix = 0;
        private int lastOutName = 0;

        RdfIri remap(RdfIri in) {
            final int p = in.getPrefixId();
            final int prefixOut;
            if (p == 0 || p == lastInPrefix) {
                prefixOut = 0;
            } else {
                lastInPrefix = p;
                final int o = prefixes.remap(p);
                prefixOut = o == lastOutPrefix ? 0 : o;
                lastOutPrefix = o;
            }
            final int n = in.getNameId();
            final int name = n == 0 ? lastInName + 1 : n;
            lastInName = name;
            final int nameOut = names.remap(name);
            final int written = nameOut == lastOutName + 1 ? 0 : nameOut;
            lastOutName = nameOut;
            return RdfIri.newInstance().setPrefixId(prefixOut).setNameId(written);
        }
    }
}
