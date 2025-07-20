# Contributing to Jelly-JVM

Jelly-JVM is an open project – **you are welcome to submit issues, pull requests, or just ask questions**!

## User & developer chat

If you have any questions or want to discuss a feature, you can join the **[Jelly Discord chat](https://discord.gg/A8sN5XwVa5)**.

## Code of conduct

By participating in this project, you are expected to follow our [Code of conduct](./code_of_conduct.md). Be kind, inclusive, and constructive.

## Submitting issues

If you have a question, found a bug, or have an idea for a new feature, please [open an issue](https://github.com/Jelly-RDF/jelly-jvm/issues/new/choose) in the **[GitHub issue tracker](https://github.com/Jelly-RDF/jelly-jvm/issues)**.

### Security issues

If you find a security issue or vulnerability, please **do not** open a public issue. Instead, **[use the dedicated vulnerability reporting page](https://github.com/Jelly-RDF/jelly-jvm/security)**.

## Pull requests

Pull requests are welcome! Simply fork the **[GitHub repository](https://github.com/Jelly-RDF/jelly-jvm)** and create a new branch for your changes. When you are ready, open a pull request to the `main` branch.

If you are working on a larger feature or a significant change, it is recommended to open an issue first to discuss the idea.

## Formatting your code

We use prettier and JHipster's [`prettier-plugin-java`](https://github.com/jhipster/prettier-java) to format our Java code. You can run the formatter with the following command:

```bash
npm install
npm run format:fix
```

The pull requests are expected to be formatted before being submitted. For IntellIJ users, you can set up the formatter to run automatically on save. To do this:

- Open Settings -> Languages & Frameworks -> JavaScript -> Prettier
    - Select `Manual Prettier configuration`
    - Select `node_modules/prettier` from project directory for `Prettier Package`
    - Check `Run on 'Reformat Code' action`
    - Check `Run on save`
    - Replace `Run for files` glob pattern with `**/*.{java}`

## Documentation

Jelly-JVM uses the exact same documentation system as the main [Jelly documentation]({{ proto_link() }}). Further information on editing the documentation can be found in the [Contributing to the Jelly documentation]({{ proto_link('contributing') }}) guide.

## Tests

We expect all pull requests to include tests for new features or bug fixes – expected coverage is 90% or higher. Tests are written in Scala using [ScalaTest](https://www.scalatest.org/). You can run the tests with the following command:

```bash
sbt test
```

Or, to test only one module (in this case, `core`):

```bash
sbt core/test
```

Some tests produce a lot of output on the console. You may want to silence it by setting the `JELLY_TEST_SILENCE_OUTPUT` environment variable to `true`. This is done for example in the CI pipeline.

## Releases

See the [dedicated page on making releases](releases.md).

## See also

- [Licensing and citation](../licensing.md)
