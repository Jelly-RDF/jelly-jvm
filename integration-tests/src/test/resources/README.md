## Test cases
### Triples
- `weather.nt` – weather data collected with the ASSIST-IoT ontology from the pilot site. Includes datatyped literals.
- `p2_ontology.nt` – OWL ontology for ASSIST-IoT Pilot 2. Includes blank nodes and language literals.
- `nt-syntax-subm-01.nt` – N-Triples test cases taken from the [N-Triples test suite](https://www.w3.org/2013/N-TriplesReports/index.html).
- `rdf-star.nt` – N-Triples-star test cases taken from [N-Triples-star Syntax Tests](https://w3c.github.io/rdf-star/tests/nt/syntax/manifest.html). This file does not include any quoted triples with blank nodes.
- `rdf-star-blanks.nt` – test cases from [N-Triples-star Syntax Tests](https://w3c.github.io/rdf-star/tests/nt/syntax/manifest.html) that include blank nodes in quoted triples.

### Quads
- `nq-syntax-tests.nq` – N-Quads test cases taken from the [N-Quads test suite](https://www.w3.org/2013/N-QuadsReports/index.html). The file includes tests named `nq-syntax-uri-*` and `nq-syntax-bnode-*`. It also includes all tests from `nt-syntax-subm-01.nt`.
- `weather-quads.nq` – several named graphs and a default graph describing mock measurements from a weather station.
