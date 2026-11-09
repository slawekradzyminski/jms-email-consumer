# Verification commands

- Unit tests: `./mvnw test`
- Targeted mutation feedback: `./mvnw -Pmutation-testing test-compile pitest:mutationCoverage`
- Native image (published image): `docker build --tag consumer:native . && scripts/native-smoke.sh consumer:native`
- JVM fallback image: `docker build --file Dockerfile.jvm --tag consumer:jvm . && .github/scripts/verify-container.sh consumer:jvm`

# Native image metadata

- Run the native build and `scripts/native-smoke.sh` after changing JMS wiring, the payload types, mail
  delivery, or upgrading Spring Boot, ActiveMQ, Jackson or Angus Mail. A green JVM test suite does not prove the
  binary: missing reflection, proxy or resource metadata only fails at runtime in the native executable.
- Application types belong in `EmailContractRuntimeHints`; library internals belong in
  `src/main/resources/META-INF/native-image/com.awesome.testing/consumer-library-hints/reachability-metadata.json`.
  Never put files in `META-INF/native-image/com.awesome.testing/consumer/`: Spring AOT overwrites that directory.
- To find new gaps, run the JVM jar under the GraalVM tracing agent
  (`java -agentlib:native-image-agent=config-output-dir=<dir> -jar target/consumer.jar` inside the GraalVM builder
  image) while exercising the broker flow, then curate only the entries the AOT output and library metadata miss.

# Mutation testing (PITest)

- Run the normal tests first, then the mutation profile after changing JMS configuration, listener behavior, email rendering, or their tests.
- Treat `NO_COVERAGE` as a reachability gap and `SURVIVED` as an assertion gap. A configuration context merely starting is not proof that the listener factory and converter honor the producer contract.
- For each meaningful survivor, add a test that passes on the original and fails on the mutant. Do not add brittle assertions solely to increase the percentage.
- Keep the score advisory until the baseline stabilizes; reject new meaningful wiring survivors before adopting a global numeric gate.
