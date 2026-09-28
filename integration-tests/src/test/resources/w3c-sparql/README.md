# W3C SPARQL test suite results

SELECT and ASK results from the [SPARQL 1.1](https://w3c.github.io/rdf-tests/sparql/sparql11/) and [SPARQL 1.2](https://w3c.github.io/rdf-tests/sparql/sparql12/) test suites, used by `W3cSparqlResultsSpec` to round-trip real result sets through Jelly-SPARQL.

Only part of the suites is here: the `.srx`, `.srj` and `.tsv` files that the manifests refer to with `mf:result`, and the manifests that refer to at least one of them. Queries, data, CONSTRUCT results (RDF graphs) and CSV results (which lose the term types) are left out. The directory layout is the same as upstream, so the relative links in the manifests still work.

- Source: https://github.com/w3c/rdf-tests, at the commit in `COMMIT`.
- License: see `LICENSE.md` (W3C test suite dual license).

## Updating

```shell
git clone --depth 1 https://github.com/w3c/rdf-tests.git /tmp/rdf-tests
python3 vendor.py /tmp/rdf-tests
```

`vendor.py` replaces the `sparql11` and `sparql12` directories, and updates `COMMIT` and `LICENSE.md`.
