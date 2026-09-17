# Java Docker Validation

Status: September 17, 2026

`CohMetrixDockerValidationTest` is the Java end-to-end regression test for
`duui-coh-metrix`. It follows the general structure of `SpaCyMultiTest` and
uses frozen bilingual CAS files as input. This tests the production path
through Java, DUUI, the Lua communication layer, Docker, and the Python service
without rerunning spaCy for every test execution.

## Test workflow

1. JUnit starts one `DUUIComposer` pipeline with the image configured in
   `CohMetrixDockerValidationTest.DOCKER_IMAGE`.
2. A compressed CAS snapshot (`input.xmi.gz`) is loaded for each testcase. It
   contains the frozen spaCy, sentence, paragraph, morphology, dependency,
   noun-chunk, and vector annotations.
3. Existing Coh-Metrix `Index` annotations and their associated
   `AnnotatorMetaData` entries are removed.
4. The CAS is processed by the Docker image.
5. The newly generated index values are checked against the corresponding
   `expected.csv`. Results are resolved through both `labelTTLab` and
   `labelV3`; no dedicated label testcases are generated.

No separate spaCy or Syntok container is started. The frozen annotations ensure
that the test specifically exposes changes in Coh-Metrix, its Lua communication
layer, and the container service.

## Current scope

- 322 bilingual testcases
- 3,496 curated index expectations
- 2,613 numeric expectations
- 883 explicit `NaN` expectations under the input-sufficiency rule
- 0 empty or uncurated expectations
- 3 additional contract tests
- 3,499 JUnit tests in total

The file
`src/test/resources/validation-bilingual/null-semantics-migration.csv`
documents expectations that were changed from a numeric value to `NaN` during
the None-semantics migration.

## Evaluation rules

- **Numeric expected value:** The result must be finite and within the stated
  tolerance. A calculated `0.0` remains a regular measurement.
- **`NaN`, `None`, or `null`:** The index cannot be computed from the available
  input. The UIMA CAS must contain `Double.NaN`.
- **Empty `expected_value`:** The test is aborted as uncurated. The current
  suite no longer contains any such values.
- **Known-issue marker:** If the notes contain `KNOWN_FAIL_*`,
  `KNOWN_ISSUE_*`, `known fail`, or `known issue`, only an actual mismatch is
  aborted. If the value matches the expectation, the assertion passes
  normally.

The three additional contract tests enforce the basic None rule:

- A completely empty document produces no numeric Coh-Metrix results.
- Sentence- and paragraph-pair indices return `NaN` when only one sentence and
  one paragraph are available.
- An available comparison pair with no noun overlap still returns the genuine
  calculated value `0.0`.

## Prerequisites

- Java 21
- Maven with the required plugins and dependencies
- a running Docker daemon
- a locally built Coh-Metrix image from the source revision under test
- the complete test resources under
  `src/test/resources/validation-bilingual`

The current test uses the local tag
`duui-coh-metrix:review-fixes-20260909`. Docker resolved the build verified on
September 17, 2026 to the following digest:

```text
duui-coh-metrix@sha256:5d63902ad2d0d83921d62c22e0bfe387ef759709b24669ee51e6b1a6d9ad751b
```

The tag name is historical and should be replaced with a unique version or
date-based tag before the final release. `latest` must not be used for a
reproducible regression test.

## Updating the test resources

The suite is stored under:

```text
src/test/resources/validation-bilingual/
```

Run `mvn clean` at least once after replacing the suite. This prevents removed
or renamed resources from an earlier run from remaining in
`target/test-classes`. The `target` directory is Maven build output and must not
be versioned as a source resource.

## Running the online test

PowerShell:

```powershell
cd C:\PATH\TO\YOUR\COH-METRIX

mvn clean -U `
  "-Dtest=CohMetrixDockerValidationTest" `
  test *>&1 |
  Tee-Object .\validation-online-coh-metrix.log
```

`-U` allows Maven to refresh metadata and retrieve missing dependencies. The
Docker image under test must already have been built from the current source
revision.

## Running the Maven-offline test

After the online run has made all Maven dependencies available:

```powershell
mvn -o `
  "-Dtest=CohMetrixDockerValidationTest" `
  test *>&1 |
  Tee-Object .\validation-offline-coh-metrix.log
```

`-o` prevents Maven network access. The Docker driver uses the image that is
already available locally. The WordNet resources `wordnet` and `omw-1.4` are
installed during the image build and do not need to be downloaded at runtime.

Maven offline mode is not the same as complete network isolation of the
container. An additional run with blocked container egress provides the
stricter proof that the service itself does not require runtime downloads.

## Final verified status

The online and Maven-offline runs on September 17, 2026 used the same image
digest and each produced:

```text
Tests run: 3499, Failures: 0, Errors: 0, Skipped: 9
BUILD SUCCESS
```

The nine aborted assertions are known and documented external discrepancies:

- 2 Pyphen resource discrepancies in English syllable counts
- 3 German `SMTEMP` discrepancies caused by frozen spaCy annotations
- 4 German GermaNet expectations for which the licensed resource is not
  included in the static test image


