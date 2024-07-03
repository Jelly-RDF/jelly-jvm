# Developer guide – releases

## Full (versioned) releases

Full (versioned) releases are created manually and follow the [Semantic Versioning](https://semver.org/) scheme for binary compatibility.

To create a new tagged release (example for version 1.2.3):
```sh
$ git checkout main
$ git pull
$ git tag v1.2.3
$ git push origin v1.2.3
```

The rest (packaging and release creation) will be handled automatically by the CI. The release will be pushed to Maven Central.

## Snapshot releases

Snapshot releases are triggered automatically by commits in the `main` branch. Snapshots are pushed to the Sonatype snapshot repository.
