# Jelly-JVM – getting started with Jena/RDF4J plugins

This guide explains how to use Jelly-JVM with Apache Jena or RDF4J as a plugin, **without writing a single line of code**. Jelly-JVM provides plugin JARs that you can simply drop in the appropriate directory to get Jelly format support in your application.

## Installation

### Apache Jena, Apache Jena Fuseki

You can simply add Jelly format support to [Apache Jena](https://jena.apache.org/index.html) or [Apacha Jena Fuseki](https://jena.apache.org/documentation/fuseki2/index.html) with Jelly's plugin JAR.

- First, download the plugin JAR. You can download the {% if jvm_version() == 'dev' %}
**[latest development version from here](https://github.com/Jelly-RDF/jelly-jvm/releases/download/dev/jelly-jena-plugin.jar)**,
{% else %}
**[version {{ jvm_version() }} from here](https://github.com/Jelly-RDF/jelly-jvm/releases/download/{{ git_tag() }}/jelly-jena-plugin.jar)**,
{% endif %} or you can go the the [releases page](https://github.com/Jelly-RDF/jelly-jvm/releases) on GitHub to download a different version of the `jelly-jena-plugin.jar` file.
    - Note that the Jelly version must be compatible with your Apache Jena version. Consult the [compatibility table](index.md#compatibility).
- Place the file in your classpath:
    - For Apache Jena Fuseki, simply place the file in `$FUSEKI_BASE/extra/` directory. `$FUSEKI_BASE` is the directory usually called `run` where you have files such as `config.ttl` and `shiro.ini`. You will most likely need to create the `extra` directory yourself.
    - For Apache Jena, place the file in the `lib/` directory of your Jena installation.
    - For other applications, consult the manual of the application.
- You can now use the Jelly format for parsing, serialization, and streaming serialization in your Jena application.

!!! warning "Content negotiation in Fuseki"

    Content negotiation using the `application/x-jelly-rdf` media type in the `Accept` header works in Fuseki since Apache Jena version 5.2.0. Previous versions of Fuseki did not support media type registration.
    

### Eclipse RDF4J

You can simply add Jelly format support to an application based on RDF4J with Jelly's plugin JAR.

- First, download the plugin JAR. You can download the {% if jvm_version() == 'dev' %}
**[latest development version from here](https://github.com/Jelly-RDF/jelly-jvm/releases/download/dev/jelly-rdf4j-plugin.jar)**,
{% else %}
**[version {{ jvm_version() }} from here](https://github.com/Jelly-RDF/jelly-jvm/releases/download/{{ git_tag() }}/jelly-rdf4j-plugin.jar)**,
{% endif %} or you can go the the [releases page](https://github.com/Jelly-RDF/jelly-jvm/releases) on GitHub to download a specific version of the `jelly-rdf4j-plugin.jar` file.
    - Note that the Jelly version must be compatible with your RDF4J version. Consult the [compatibility table](index.md#compatibility).
- Place the file in your classpath. Consult the manual of your application for the exact location.
- You can now use the Jelly format for parsing and serialization in your RDF4J application.

## Supported features

The Jelly-JVM plugin JARs provide the following features:

- Full support for parsing and serialization of RDF data (triples and quads) in the Jelly format.
    - The parser will automatically detect if the input data [is delimited or not]({{ proto_link('user-guide#delimited-vs-non-delimited-jelly') }}). Both delimited and non-delimited Jelly data can be parsed.
    - In Apache Jena also the stream serialization is supported.
- Recognizing the `.jelly` file extension.
- Recognizing the `application/x-jelly-rdf` media type.

The Jelly format is registered under the name `jelly` in the RDF libraries, so you can use it in the same way as other formats like Turtle, RDF/XML, or JSON-LD.

## See also

- **[Getting started for developers](getting-started-devs.md)** – if you want to get your hands dirty with code and get more features out of Jelly.
