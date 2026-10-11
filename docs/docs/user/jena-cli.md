Jelly-JVM fully supports Apache Jena's command-line interface (CLI) utilities.

## Parsing

Jena will automatically detect Jelly files based on their extension (`.jelly`, `.jelly.gz`) and parse them. You can also manually set the `--syntax` option to `jelly`.

## Writing

You can use Jelly as an output format for Jena's CLI utilities by specifying the `--output` or `--stream` options with the `jelly` format. We recommend using the `--stream` option for better performance. 

!!! example "Example: converting a Turtle file to Jelly"

    ```shell
    ./riot --stream=jelly data.ttl > data.jelly
    ```

By default Jena will use the "big, strict" Jelly preset (name table: 4000 entries, prefix table: 150, datatype table: 32). You may want to change it to better fit your data, for example to use less memory with small files.

The following presets are available:

- `SMALL_STRICT` – 128 name table entries, 16 prefix table entries, 16 datatype table entries
- `BIG_STRICT` **(default)** – 4000 name table entries, 150 prefix table entries, 32 datatype table entries (recommended for larger files)

The presets `SMALL_GENERALIZED`, `SMALL_RDF_STAR`, `SMALL_ALL_FEATURES`, `BIG_GENERALIZED`, `BIG_RDF_STAR` and `BIG_ALL_FEATURES` still work, but are deprecated and will be removed in Jelly-JVM 5.0.0. Their RDF-star and generalized RDF flags only apply to Jelly-RDF 1.0 and 1.1 output. Jelly-RDF 1.2, which Jena writes, never has generalized statements and always allows triple terms as objects, so for it they are the same as `SMALL_STRICT` and `BIG_STRICT`.

To use one of these presets, use the `--set` CLI option with the `https://neverblink.eu/jelly/riot/symbols#preset` symbol:

!!! example "Example: converting a Turtle file to Jelly with a big preset (strict)"

    ```shell
    ./riot --stream=jelly \
        --set="https://neverblink.eu/jelly/riot/symbols#preset=BIG_STRICT" \
        data.ttl > data.jelly
    ```

!!! example "Example: dumping a TDB2 database to Jelly with a small preset"

    ```shell
    ./tdb2.tdbdump --tdb=path/to/assembler.ttl \
        --set="https://neverblink.eu/jelly/riot/symbols#preset=SMALL_STRICT" \
        --stream=jelly > mydb.jelly
    ```

## See also

- [Installing Jelly with Jena](../getting-started-plugins.md#apache-jena-apache-jena-fuseki)
- [Jena CLI documentation](https://jena.apache.org/documentation/tools/index.html)
