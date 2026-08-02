# Verification commands

- Unit tests: `./mvnw test`
- Targeted mutation feedback: `./mvnw -Pmutation-testing test-compile pitest:mutationCoverage`

# Mutation-testing workflow

- Run the normal tests first, then the mutation profile after changing JMS configuration, listener behavior, email rendering, or their tests.
- Treat `NO_COVERAGE` as a reachability gap and `SURVIVED` as an assertion gap. A configuration context merely starting is not proof that the listener factory and converter honor the producer contract.
- For each meaningful survivor, add a test that passes on the original and fails on the mutant. Do not add brittle assertions solely to increase the percentage.
- Keep the score advisory until the baseline stabilizes; reject new meaningful wiring survivors before adopting a global numeric gate.
