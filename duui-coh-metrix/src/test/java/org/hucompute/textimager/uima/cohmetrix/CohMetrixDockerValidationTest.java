package org.hucompute.textimager.uima.cohmetrix;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opentest4j.TestAbortedException;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIDockerDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end regression tests for Coh-Metrix.
 *
 * <p>The fixtures are CAS snapshots produced by validation_bilingual(8). They
 * contain the frozen spaCy annotations. Existing Coh-Metrix output annotations
 * are removed before the CAS is sent through the pinned Docker image.</p>
 */
@Execution(ExecutionMode.SAME_THREAD)
public class CohMetrixDockerValidationTest {
//    static final String DOCKER_IMAGE =
//            "duui-coh-metrix:review-fixes-20260909";
    static final String DOCKER_IMAGE =
            "docker.texttechnologylab.org/duui-coh-metrix:0.1.1";

    static final String INDEX_TYPE =
            "org.texttechnologylab.uima.type.cohmetrix.Index";
    static final String META_TYPE =
            "org.texttechnologylab.annotation.AnnotatorMetaData";
    static final String MANIFEST =
            "/validation-bilingual/manifest.csv";

    /**
     * Known issues must be marked explicitly. The second alternative keeps
     * compatibility with the existing GermaNet fixtures, which use the
     * legacy marker "KNOWN ISSUE:" instead of a structured marker.
     */
    static final Pattern KNOWN_ISSUE_MARKER = Pattern.compile(
            "\\bKNOWN_(?:FAIL|ISSUE)_[A-Z0-9_]+\\b|\\bKNOWN ISSUE\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Text Easability principal-component scores are explicit placeholders in
     * the current component. They require the unavailable TASA LSA model and
     * regression weights and therefore deliberately return NaN plus a stable
     * "Not implemented" diagnostic. Both Coh-Metrix labels and their TTLab
     * Wikipedia aliases are accepted by normalizing the alias below.
     */
    static final Set<String> TEXT_EASABILITY_PC_LABELS = Set.of(
            "PCNARz", "PCNARp", "PCSYNz", "PCSYNp",
            "PCCNCz", "PCCNCp", "PCREFz", "PCREFp",
            "PCDCz", "PCDCp", "PCVERBz", "PCVERBp",
            "PCCONNz", "PCCONNp", "PCTEMPz", "PCTEMPp"
    );
    static final String TEXT_EASABILITY_NOT_IMPLEMENTED_PREFIX =
            "Not implemented: Text Easability PC scores require LSA model "
                    + "+ regression weights trained on TASA corpus";

    static DUUIComposer composer;
    static final Map<String, ResultSnapshot> resultCache = new HashMap<>();
    static List<CaseSpec> cases;

    @BeforeAll
    static void beforeAll() throws Exception {
        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(new DUUILuaContext().withJsonLibrary());
        composer.addDriver(new DUUIDockerDriver());
        composer.add(new DUUIDockerDriver.Component(DOCKER_IMAGE));
        cases = readManifest();
        assertFalse(cases.isEmpty(), "No validation fixtures found");
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (composer != null) {
            composer.shutdown();
        }
    }

    @TestFactory
    Stream<DynamicNode> validationBilingual() {
        return cases.stream().map(testCase -> {
            List<ExpectedValue> expectations = readExpected(testCase.expectedResource());
            Stream<DynamicNode> assertions = expectations.stream().map(expected ->
                    DynamicTest.dynamicTest(expected.label(), () -> {
                        if (!expected.hasExpectation()) {
                            throw new TestAbortedException(
                                    "No curated expected value in expected.csv"
                            );
                        }

                        ResultSnapshot result = resultFor(testCase);
                        Double actual = requireCleanResult(result, expected.label());

                        try {
                            assertExpectedValue(actual, expected);
                        } catch (AssertionError failure) {
                            if (isKnownIssue(expected.notes())) {
                                throw new TestAbortedException(
                                        "Known issue: " + expected.notes(),
                                        failure
                                );
                            }
                            throw failure;
                        }
                    })
            );

            return DynamicContainer.dynamicContainer(testCase.displayName(), assertions);
        });
    }

    @Test
    void emptyDocumentHasNoNumericCohMetrixResults() throws Exception {
        JCas cas = JCasFactory.createJCas();
        cas.setDocumentLanguage("en");
        cas.setDocumentText("");

        ResultSnapshot result = run(cas);
        assertFalse(result.values().isEmpty(), "Docker component returned no indices");
        result.values().forEach((label, ignored) -> {
            if (isIntentionallyUnimplementedTextEasability(label)) {
                assertIntentionallyUnimplementedResult(result, label);
                return;
            }
            Double value = requireCleanResult(result, label);
            assertTrue(Double.isNaN(value),
                    () -> label + " must be NaN for an empty document, but was " + value);
        });
    }

    @Test
    void pairIndicesAreNaNWhenOnlyOneSentenceAndOneParagraphExist() {
        CaseSpec testCase = requireCase(
                "Descriptive_en",
                "tc001_en_single_sentence"
        );
        ResultSnapshot result = resultFor(testCase);

        List<String> pairIndices = List.of(
                "CRFNO1", "CRFNOa", "CRFAO1", "CRFAOa", "CRFSO1", "CRFSOa",
                "CRFCWO1", "CRFCWO1d", "CRFCWOa", "CRFCWOad",
                "LSASS1", "LSASS1d", "LSASSp", "LSASSpd", "LSAPP1", "LSAPP1d",
                "SYNMEDpos", "SYNMEDwrd", "SYNMEDlem", "SYNSTRUTa", "SYNSTRUTt",
                "SMTEMP"
        );

        pairIndices.forEach(label -> {
            Double value = requireCleanResult(result, label);
            assertTrue(Double.isNaN(value),
                    () -> label + " needs a comparison pair and must be NaN, but was " + value);
        });
    }

    @Test
    void genuineCalculatedZeroRemainsZero() {
        CaseSpec testCase = requireCase(
                "Referential Cohesion_en",
                "tc001_en_crfcwo_surface_form_not_lemma"
        );
        Double value = requireCleanResult(resultFor(testCase), "CRFNO1");

        assertFalse(Double.isNaN(value), "CRFNO1 has a valid sentence pair");
        assertEquals(0.0, value, 1e-12,
                "No noun overlap in a valid pair is a genuine zero result");
    }

    private static synchronized ResultSnapshot resultFor(CaseSpec testCase) {
        return resultCache.computeIfAbsent(testCase.cacheKey(), ignored -> {
            try {
                return run(loadCas(testCase.xmiResource()));
            } catch (Exception e) {
                throw new IllegalStateException("Could not execute " + testCase.displayName(), e);
            }
        });
    }

    private static synchronized ResultSnapshot run(JCas jCas) throws Exception {
        removePreviousCohMetrixOutput(jCas.getCas());
        composer.run(jCas);
        return readResults(jCas.getCas());
    }

    private static JCas loadCas(String resource) throws Exception {
        JCas jCas = JCasFactory.createJCas();
        try (InputStream raw = resource(resource);
             GZIPInputStream gzip = new GZIPInputStream(raw)) {
            XmiCasDeserializer.deserialize(gzip, jCas.getCas(), true);
        }
        return jCas;
    }

    private static void removePreviousCohMetrixOutput(CAS cas) {
        Type indexType = requireType(cas, INDEX_TYPE);
        List<FeatureStructure> oldIndices = indexed(cas, indexType);
        Set<FeatureStructure> oldIndexSet = new HashSet<>(oldIndices);

        Type metaType = cas.getTypeSystem().getType(META_TYPE);
        if (metaType != null) {
            Feature reference = metaType.getFeatureByBaseName("reference");
            if (reference != null) {
                for (FeatureStructure meta : indexed(cas, metaType)) {
                    if (oldIndexSet.contains(meta.getFeatureValue(reference))) {
                        cas.removeFsFromIndexes(meta);
                    }
                }
            }
        }

        oldIndices.forEach(cas::removeFsFromIndexes);
    }

    private static ResultSnapshot readResults(CAS cas) {
        Type indexType = requireType(cas, INDEX_TYPE);
        Feature labelV3 = requireFeature(indexType, "labelV3");
        Feature labelTTLab = requireFeature(indexType, "labelTTLab");
        Feature valueFeature = requireFeature(indexType, "value");
        Feature errorFeature = indexType.getFeatureByBaseName("error");

        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();

        for (FeatureStructure index : indexed(cas, indexType)) {
            String v3 = index.getStringValue(labelV3);
            String ttlab = index.getStringValue(labelTTLab);
            double value = index.getDoubleValue(valueFeature);
            String error = errorFeature == null ? null : index.getStringValue(errorFeature);

            putLabel(values, errors, ttlab, value, error, false);
            putLabel(values, errors, v3, value, error, true);
        }

        return new ResultSnapshot(values, errors);
    }

    private static void putLabel(
            Map<String, Double> values,
            Map<String, String> errors,
            String label,
            double value,
            String error,
            boolean firstWins
    ) {
        if (label == null || label.isBlank() || label.equalsIgnoreCase("n/a")) {
            return;
        }
        if (firstWins) {
            values.putIfAbsent(label, value);
            errors.putIfAbsent(label, error);
        } else {
            values.put(label, value);
            errors.put(label, error);
        }
    }

    /**
     * Checks output integrity before any known-issue handling. A missing
     * index, component exception or infinite value is always a regression and
     * must never be converted into an aborted known-issue test.
     */
    private static Double requireCleanResult(ResultSnapshot result, String label) {
        Double actual = result.values().get(label);
        assertNotNull(actual, () -> "Docker output does not contain " + label);

        String error = result.errors().get(label);
        assertTrue(error == null || error.isBlank(), () ->
                label + " returned a component error: " + error);
        assertFalse(Double.isInfinite(actual), () ->
                label + " returned an infinite value: " + actual);

        return actual;
    }

    private static boolean isIntentionallyUnimplementedTextEasability(String label) {
        String normalized = label.endsWith("_wikipedia")
                ? label.substring(0, label.length() - "_wikipedia".length())
                : label;
        return TEXT_EASABILITY_PC_LABELS.contains(normalized);
    }

    private static void assertIntentionallyUnimplementedResult(
            ResultSnapshot result,
            String label
    ) {
        Double actual = result.values().get(label);
        assertNotNull(actual, () -> "Docker output does not contain " + label);
        assertTrue(Double.isNaN(actual), () ->
                label + " is not implemented and must be NaN, but was " + actual);

        String error = result.errors().get(label);
        assertNotNull(error, () ->
                label + " is not implemented but returned no diagnostic");
        assertTrue(error.startsWith(TEXT_EASABILITY_NOT_IMPLEMENTED_PREFIX), () ->
                label + " returned an unexpected placeholder diagnostic: " + error);
    }

    /**
     * Compares only the value. Consequently, the caller may treat a mismatch
     * as a known issue without hiding structural or component-level failures.
     */
    private static void assertExpectedValue(Double actual, ExpectedValue expected) {
        if (expected.notComputable()) {
            assertTrue(Double.isNaN(actual), () ->
                    expected.label() + " must be NaN (not computable), but was " + actual);
            return;
        }

        assertTrue(Double.isFinite(actual), () ->
                expected.label() + " must be numeric, but was " + actual);
        assertEquals(expected.value(), actual, expected.tolerance(), () ->
                expected.label() + " expected " + expected.value()
                        + " +/- " + expected.tolerance() + ", but was " + actual);
    }

    private static boolean isKnownIssue(String notes) {
        return notes != null && KNOWN_ISSUE_MARKER.matcher(notes).find();
    }

    private static List<CaseSpec> readManifest() throws IOException {
        List<CaseSpec> result = new ArrayList<>();
        try (BufferedReader reader = utf8(resource(MANIFEST));
             var records = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord row : records) {
                result.add(new CaseSpec(
                        row.get("suite"),
                        row.get("case_id"),
                        row.get("xmi_resource"),
                        row.get("expected_resource")
                ));
            }
        }
        return result;
    }

    private static List<ExpectedValue> readExpected(String path) {
        List<ExpectedValue> result = new ArrayList<>();
        try (BufferedReader reader = utf8(resource(path));
             var records = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord row : records) {
                String rawValue = row.get("expected_value").trim();
                boolean hasExpectation = !rawValue.isEmpty();
                boolean notComputable = Set.of("nan", "none", "null")
                        .contains(rawValue.toLowerCase(Locale.ROOT));
                Double value = hasExpectation && !notComputable
                        ? Double.valueOf(rawValue)
                        : null;
                String rawTolerance = row.get("tolerance").trim();
                double tolerance = rawTolerance.isEmpty()
                        ? 0.0
                        : Double.parseDouble(rawTolerance);

                result.add(new ExpectedValue(
                        row.get("index"),
                        value,
                        tolerance,
                        row.get("notes"),
                        hasExpectation,
                        notComputable
                ));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
        return result;
    }

    private static CaseSpec requireCase(String suite, String caseId) {
        return cases.stream()
                .filter(testCase -> testCase.suite().equals(suite)
                        && testCase.caseId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fixture not found: " + suite + "/" + caseId
                ));
    }

    private static Type requireType(CAS cas, String name) {
        Type type = cas.getTypeSystem().getType(name);
        if (type == null) {
            throw new IllegalStateException("Type system does not contain " + name);
        }
        return type;
    }

    private static Feature requireFeature(Type type, String name) {
        Feature feature = type.getFeatureByBaseName(name);
        if (feature == null) {
            throw new IllegalStateException(type.getName() + " has no feature " + name);
        }
        return feature;
    }

    private static List<FeatureStructure> indexed(CAS cas, Type type) {
        List<FeatureStructure> result = new ArrayList<>();
        FSIterator<FeatureStructure> iterator =
                cas.getIndexRepository().getAllIndexedFS(type);
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static InputStream resource(String path) {
        InputStream stream = CohMetrixDockerValidationTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Classpath resource not found: " + path);
        }
        return stream;
    }

    private static BufferedReader utf8(InputStream input) {
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    record CaseSpec(
            String suite,
            String caseId,
            String xmiResource,
            String expectedResource
    ) {
        String cacheKey() {
            return suite + "/" + caseId;
        }

        String displayName() {
            return cacheKey();
        }
    }

    record ExpectedValue(
            String label,
            Double value,
            double tolerance,
            String notes,
            boolean hasExpectation,
            boolean notComputable
    ) {
    }

    record ResultSnapshot(
            Map<String, Double> values,
            Map<String, String> errors
    ) {
    }
}
