package eu.neverblink.jelly.core.patch.internal;

import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.RdfTerm;
import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.patch.JellyPatchOptions;
import eu.neverblink.jelly.core.patch.PatchDecoder;
import eu.neverblink.jelly.core.patch.PatchHandler;
import eu.neverblink.jelly.core.proto.v1.*;

public abstract class PatchDecoderImpl<TNode, TDatatype> extends DecoderBase<TNode, TDatatype> implements PatchDecoder {

    protected final PatchHandler<TNode> patchHandler;
    protected final RdfPatchOptions supportedOptions;

    private RdfPatchOptions currentOptions;
    private boolean isFrameStreamType = false;
    private boolean isPunctuatedStreamType = false;

    protected PatchDecoderImpl(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        PatchHandler<TNode> patchHandler,
        RdfPatchOptions supportedOptions
    ) {
        super(converter);
        this.patchHandler = patchHandler;
        this.supportedOptions = supportedOptions != null
            ? supportedOptions
            : JellyPatchOptions.DEFAULT_SUPPORTED_OPTIONS;
    }

    @Override
    protected int getNameTableSize() {
        if (currentOptions == null) {
            return JellyOptions.SMALL_NAME_TABLE_SIZE;
        }

        return currentOptions.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        if (currentOptions == null) {
            return JellyOptions.SMALL_PREFIX_TABLE_SIZE;
        }

        return currentOptions.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        if (currentOptions == null) {
            return JellyOptions.SMALL_DT_TABLE_SIZE;
        }

        return currentOptions.getMaxDatatypeTableSize();
    }

    @Override
    public RdfPatchOptions getPatchOptions() {
        return currentOptions;
    }

    @Override
    public void ingestRow(RdfPatchRow row) {
        switch (row.getRowCase()) {
            case OPTIONS -> handleOptions(row.getOptions());
            case STATEMENT_ADD -> handleStatementAdd(row.getStatementAdd());
            case STATEMENT_DELETE -> handleStatementDelete(row.getStatementDelete());
            case NAMESPACE_ADD -> handleNamespaceAdd(row.getNamespaceAdd());
            case NAMESPACE_DELETE -> handleNamespaceDelete(row.getNamespaceDelete());
            case TRANSACTION_START -> handleTransactionStart();
            case TRANSACTION_COMMIT -> handleTransactionCommit();
            case TRANSACTION_ABORT -> handleTransactionAbort();
            case NAME -> handleName(row.getName());
            case PREFIX -> handlePrefix(row.getPrefix());
            case DATATYPE -> handleDatatype(row.getDatatype());
            case HEADER -> handleHeader(row.getHeader());
            case PUNCTUATION -> handlePunctuation();
            case ROW_NOT_SET -> throw new RdfProtoDeserializationError(
                "Row kind is not set or unknown: " + row.getRowCase()
            );
        }
    }

    @Override
    public void ingestFrame(RdfPatchFrame frame) {
        for (final var row : frame.getRowsList()) {
            ingestRow(row);
        }

        if (isFrameStreamType) {
            patchHandler.punctuation();
        }
    }

    protected abstract void handleStatementAdd(RdfQuad statement);

    protected abstract void handleStatementDelete(RdfQuad statement);

    protected void handleOptions(RdfPatchOptions opt) {
        JellyPatchOptions.checkCompatibility(opt, supportedOptions);
        setPatchOptions(opt);
    }

    private void handleNamespaceAdd(RdfPatchNamespace nsRow) {
        final var valueIri = RdfTerm.from(nsRow.getValue());
        final var graphIri = RdfTerm.from(nsRow.hasGraph() ? nsRow.getGraph() : null);
        patchHandler.addNamespace(
            nsRow.getName(),
            nameDecoder.provide().decode(valueIri.prefixId(), valueIri.nameId()),
            decodeNsIri(graphIri)
        );
    }

    private void handleNamespaceDelete(RdfPatchNamespace nsRow) {
        final var valueIri = RdfTerm.from(nsRow.hasValue() ? nsRow.getValue() : null);
        final var graphIri = RdfTerm.from(nsRow.hasGraph() ? nsRow.getGraph() : null);
        patchHandler.deleteNamespace(nsRow.getName(), decodeNsIri(valueIri), decodeNsIri(graphIri));
    }

    private void handleTransactionStart() {
        patchHandler.transactionStart();
    }

    private void handleTransactionCommit() {
        patchHandler.transactionCommit();
    }

    private void handleTransactionAbort() {
        patchHandler.transactionAbort();
    }

    private void handleName(RdfNameEntry name) {
        nameDecoder.provide().updateNames(name);
    }

    private void handlePrefix(RdfPrefixEntry prefix) {
        nameDecoder.provide().updatePrefixes(prefix);
    }

    private void handleDatatype(RdfDatatypeEntry dtRow) {
        datatypeLookup.provide().update(dtRow.getId(), converter.makeDatatype(dtRow.getValue()));
    }

    private void handleHeader(RdfPatchHeader hRow) {
        // No support for repeated terms in the header
        final var term =
            switch (hRow.getValueCase()) {
                case H_IRI -> RdfTerm.from(hRow.getHIri());
                case H_BNODE -> RdfTerm.from(hRow.getHBnode());
                case H_LITERAL -> RdfTerm.from(hRow.getHLiteral());
                case H_TRIPLE_TERM -> RdfTerm.from(hRow.getHTripleTerm());
                case VALUE_NOT_SET -> null;
            };

        patchHandler.header(hRow.getKey(), convertTerm(term));
    }

    private void handlePunctuation() {
        if (!isPunctuatedStreamType) {
            throw new RdfProtoDeserializationError("Unexpected punctuation row in non-punctuated stream.");
        }

        patchHandler.punctuation();
    }

    private void setPatchOptions(RdfPatchOptions options) {
        if (currentOptions != null) {
            return;
        }

        this.currentOptions = options;
        this.isFrameStreamType = options.getStreamType() == PatchStreamType.PATCH_STREAM_TYPE_FRAME;
        this.isPunctuatedStreamType = options.getStreamType() == PatchStreamType.PATCH_STREAM_TYPE_PUNCTUATED;
    }

    private TNode decodeNsIri(RdfTerm.Iri iri) {
        if (iri == null) {
            return null;
        }

        return nameDecoder.provide().decode(iri.prefixId(), iri.nameId());
    }

    public static class TriplesDecoder<TNode, TDatatype> extends PatchDecoderImpl<TNode, TDatatype> {

        private final PatchHandler.TriplePatchHandler<TNode> patchHandler;

        public TriplesDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            PatchHandler.TriplePatchHandler<TNode> patchHandler,
            RdfPatchOptions supportedOptions
        ) {
            super(converter, patchHandler, supportedOptions);
            this.patchHandler = patchHandler;
        }

        @Override
        protected void handleOptions(RdfPatchOptions opt) {
            if (opt.getStatementType() != PatchStatementType.PATCH_STATEMENT_TYPE_TRIPLES) {
                throw new RdfProtoDeserializationError(
                    "Incoming stream with statement type %s cannot be decoded by this decoder. Only TRIPLES streams are accepted.".formatted(
                            opt.getStatementType()
                        )
                );
            }
            super.handleOptions(opt);
        }

        @Override
        protected void handleStatementAdd(RdfQuad statement) {
            final var term = RdfTerm.from(statement);
            patchHandler.addTriple(
                convertSubjectTermWrapped(term.subject()),
                convertPredicateTermWrapped(term.predicate()),
                convertObjectTermWrapped(term.object())
            );
        }

        @Override
        protected void handleStatementDelete(RdfQuad statement) {
            final var term = RdfTerm.from(statement);
            patchHandler.deleteTriple(
                convertSubjectTermWrapped(term.subject()),
                convertPredicateTermWrapped(term.predicate()),
                convertObjectTermWrapped(term.object())
            );
        }
    }

    public static class QuadsDecoder<TNode, TDatatype> extends PatchDecoderImpl<TNode, TDatatype> {

        private final PatchHandler.QuadPatchHandler<TNode> patchHandler;

        public QuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            PatchHandler.QuadPatchHandler<TNode> patchHandler,
            RdfPatchOptions supportedOptions
        ) {
            super(converter, patchHandler, supportedOptions);
            this.patchHandler = patchHandler;
        }

        @Override
        protected void handleOptions(RdfPatchOptions opt) {
            if (opt.getStatementType() != PatchStatementType.PATCH_STATEMENT_TYPE_QUADS) {
                throw new RdfProtoDeserializationError(
                    "Incoming stream with statement type %s cannot be decoded by this decoder. Only QUADS streams are accepted.".formatted(
                            opt.getStatementType()
                        )
                );
            }
            super.handleOptions(opt);
        }

        @Override
        protected void handleStatementAdd(RdfQuad statement) {
            final var term = RdfTerm.from(statement);
            patchHandler.addQuad(
                convertSubjectTermWrapped(term.subject()),
                convertPredicateTermWrapped(term.predicate()),
                convertObjectTermWrapped(term.object()),
                convertGraphTermWrapped(term.graph())
            );
        }

        @Override
        protected void handleStatementDelete(RdfQuad statement) {
            final var term = RdfTerm.from(statement);
            patchHandler.deleteQuad(
                convertSubjectTermWrapped(term.subject()),
                convertPredicateTermWrapped(term.predicate()),
                convertObjectTermWrapped(term.object()),
                convertGraphTermWrapped(term.graph())
            );
        }
    }

    public static class AnyStatementDecoder<TNode, TDatatype> extends PatchDecoderImpl<TNode, TDatatype> {

        private final PatchHandler.AnyPatchHandler<TNode> patchHandler;
        private PatchStatementType statementType = null;

        public AnyStatementDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            PatchHandler.AnyPatchHandler<TNode> patchHandler,
            RdfPatchOptions supportedOptions
        ) {
            super(converter, patchHandler, supportedOptions);
            this.patchHandler = patchHandler;
        }

        @Override
        protected void handleOptions(RdfPatchOptions opt) {
            if (opt.getStatementType() == PatchStatementType.PATCH_STATEMENT_TYPE_UNSPECIFIED) {
                throw new RdfProtoDeserializationError("Incoming stream has no statement type set. Cannot decode.");
            }
            if (opt.getStatementType() == PatchStatementType.UNRECOGNIZED) {
                throw new RdfProtoDeserializationError(
                    "Incoming stream with statement type %s cannot be decoded by this decoder. Only TRIPLES and QUADS streams are accepted.".formatted(
                            opt.getStatementType()
                        )
                );
            }

            this.statementType = opt.getStatementType();
            super.handleOptions(opt);
        }

        @Override
        protected void handleStatementAdd(RdfQuad statement) {
            final var term = RdfTerm.from(statement);
            final var s = convertSubjectTermWrapped(term.subject());
            final var p = convertPredicateTermWrapped(term.predicate());
            final var o = convertObjectTermWrapped(term.object());

            if (statementType == null) {
                throw new RdfProtoDeserializationError(
                    "Statement type is not set, statement add command cannot be decoded."
                );
            }
            switch (statementType) {
                case PATCH_STATEMENT_TYPE_TRIPLES -> patchHandler.addTriple(s, p, o);
                case PATCH_STATEMENT_TYPE_QUADS -> {
                    final var g = convertGraphTermWrapped(term.graph());
                    patchHandler.addQuad(s, p, o, g);
                }
            }
        }

        @Override
        protected void handleStatementDelete(RdfQuad statement) {
            final var term = RdfTerm.from(statement);
            final var s = convertSubjectTermWrapped(term.subject());
            final var p = convertPredicateTermWrapped(term.predicate());
            final var o = convertObjectTermWrapped(term.object());

            if (statementType == null) {
                throw new RdfProtoDeserializationError(
                    "Statement type is not set, statement delete command cannot be decoded."
                );
            }
            switch (statementType) {
                case PATCH_STATEMENT_TYPE_TRIPLES -> patchHandler.deleteTriple(s, p, o);
                case PATCH_STATEMENT_TYPE_QUADS -> {
                    final var g = convertGraphTermWrapped(term.graph());
                    patchHandler.deleteQuad(s, p, o, g);
                }
            }
        }
    }
}
