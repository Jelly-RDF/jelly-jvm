Jelly-JVM fully supports Apache Jena's command-line interface (CLI) utilities.

## Parsing

Jena will automatically detect Jelly files based on their extension (`.jelly`, `.jelly.gz`) and parse them. You can also manually set the `--syntax` option to `jelly`.

## Writing

You can use Jelly as an output format for Jena's CLI utilities by specifying the `--output` or `--stream` options with the `jelly` format. We recommend using the `--stream` option for better performance. 

!!! example "Example: converting a Turtle file to Jelly"

    ```shell
    ./riot --stream=jelly data.ttl > data.jelly
    ```

By default Jena will use the "big, all features" Jelly preset (name table: 4000 entries, prefix table: 128, datatype table: 32, RDF-star enabled, generalized RDF enabled). There are a few reasons why you might want to change these serialization options:

- **Performance** – you may want to tweak the settings to better fit your data.
- **Compatibility** – if your data does not include RDF-star or generalized RDF, you can mark these features as disabled. Later, parsers will know accurately what to expect in your data.

The following presets are available:

- Small: 128 name table entries, 16 prefix table entries, 16 datatype table entries
    - `SMALL_STRICT` – RDF-star and generalized RDF disabled
    - `SMALL_GENERALIZED` – RDF-star disabled, generalized RDF enabled
    - `SMALL_RDF_STAR` – RDF-star enabled, generalized RDF disabled
    - `SMALL_ALL_FEATURES` – RDF-star and generalized RDF enabled
- Big: 4000 name table entries, 150 prefix table entries, 32 datatype table entries **(recommended for larger files)**
    - `BIG_STRICT`
    - `BIG_GENERALIZED`
    - `BIG_RDF_STAR`
    - `BIG_ALL_FEATURES` **(default)**

To use one of these presets, use the `--set` CLI option with the `https://neverblink.eu/jelly/riot/symbols#preset` symbol:

!!! example "Example: converting a Turtle file to Jelly with a big preset (strict)"

    ```shell
    ./riot --stream=jelly \
        --set="https://neverblink.eu/jelly/riot/symbols#preset=BIG_STRICT" \
        data.ttl > data.jelly
    ```

!!! example "Example: dumping a TDB2 database to Jelly with a big preset (all features)"

    ```shell
    ./tdb2.tdbdump --tdb=path/to/assembler.ttl \
        --set="https://neverblink.eu/jelly/riot/symbols#preset=BIG_ALL_FEATURES" \
        --stream=jelly > mydb.jelly
    ```

## See also

- [Installing Jelly with Jena](../getting-started-plugins.md#apache-jena-apache-jena-fuseki)
- [Jena CLI documentation](https://jena.apache.org/documentation/tools/index.html)
