/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.otel.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.ArrayValue;
import io.opentelemetry.proto.common.v1.InstrumentationLibrary;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.common.v1.KeyValueList;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Status;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.model.trace.DefaultLink;
import org.opensearch.dataprepper.model.trace.DefaultSpanEvent;
import org.opensearch.dataprepper.model.trace.DefaultTraceGroupFields;
import org.opensearch.dataprepper.model.trace.JacksonSpan;
import org.opensearch.dataprepper.model.trace.Link;
import org.opensearch.dataprepper.model.trace.Span;
import org.opensearch.dataprepper.model.trace.SpanEvent;
import org.opensearch.dataprepper.model.trace.TraceGroupFields;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OTelProtoCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Random RANDOM = new Random();
    private static final String TEST_REQUEST_JSON_FILE = "test-request.json";
    private static final String TEST_SPAN_EVENT_JSON_FILE = "test-span-event.json";

    private final OTelProtoCodec.OTelProtoDecoder decoderUnderTest = new OTelProtoCodec.OTelProtoDecoder();
    private final OTelProtoCodec.OTelProtoEncoder encoderUnderTest = new OTelProtoCodec.OTelProtoEncoder();

    private byte[] getRandomBytes(int len) {
        byte[] bytes = new byte[len];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private Map<String, Object> returnMap(final String jsonStr) throws JsonProcessingException {
        return (Map<String, Object>) OBJECT_MAPPER.readValue(jsonStr, Map.class);
    }

    private List<Object> returnList(final String jsonStr) throws JsonProcessingException {
        return (List<Object>) OBJECT_MAPPER.readValue(jsonStr, List.class);
    }

    private ExportTraceServiceRequest buildExportTraceServiceRequestFromJsonFile(String requestJsonFileName) throws IOException {
        final StringBuilder jsonBuilder = new StringBuilder();
        try (final InputStream inputStream = Objects.requireNonNull(
                OTelProtoCodecTest.class.getClassLoader().getResourceAsStream(requestJsonFileName))){
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            bufferedReader.lines().forEach(jsonBuilder::append);
        }
        final String requestJson = jsonBuilder.toString();
        final ExportTraceServiceRequest.Builder builder = ExportTraceServiceRequest.newBuilder();
        JsonFormat.parser().merge(requestJson, builder);
        return builder.build();
    }

    @Nested
    class OTelProtoDecoderTest {
        @Test
        public void testParseExportTraceServiceRequest() throws IOException {
            final ExportTraceServiceRequest exportTraceServiceRequest = buildExportTraceServiceRequestFromJsonFile(TEST_REQUEST_JSON_FILE);
            final List<Span> spans = decoderUnderTest.parseExportTraceServiceRequest(exportTraceServiceRequest);
            assertThat(spans.size(), is(equalTo(3)));
            for (final Span span: spans) {
                if (span.getParentSpanId().isEmpty()) {
                    assertThat(span.getTraceGroup(), notNullValue());
                    assertThat(span.getTraceGroupFields().getEndTime(), notNullValue());
                    assertThat(span.getTraceGroupFields().getDurationInNanos(), notNullValue());
                    assertThat(span.getTraceGroupFields().getStatusCode(), notNullValue());
                } else {
                    assertThat(span.getTraceGroup(), nullValue());
                    assertThat(span.getTraceGroupFields().getEndTime(), nullValue());
                    assertThat(span.getTraceGroupFields().getDurationInNanos(), nullValue());
                    assertThat(span.getTraceGroupFields().getStatusCode(), nullValue());
                }
                Map<String, Object> attributes = span.getAttributes();
                assertThat(attributes.containsKey(OTelProtoCodec.RESOURCE_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply("service.name")), is(true));
                assertThat(attributes.containsKey(OTelProtoCodec.INSTRUMENTATION_LIBRARY_NAME), is(true));
                assertThat(attributes.containsKey(OTelProtoCodec.STATUS_CODE), is(true));
            }
        }

        @Test
        public void testGetSpanEvent() {
            final String testName = "test name";
            final long testTimeNanos = System.nanoTime();
            final String testTime = OTelProtoCodec.convertUnixNanosToISO8601(testTimeNanos);
            final String testKey = "test key";
            final String testValue = "test value";
            io.opentelemetry.proto.trace.v1.Span.Event testOTelProtoSpanEvent = io.opentelemetry.proto.trace.v1.Span.Event.newBuilder()
                    .setName(testName)
                    .setTimeUnixNano(testTimeNanos)
                    .setDroppedAttributesCount(0)
                    .addAttributes(KeyValue.newBuilder().setKey(testKey).setValue(AnyValue.newBuilder()
                            .setStringValue(testValue).build()).build())
                    .build();
            final SpanEvent result = decoderUnderTest.getSpanEvent(testOTelProtoSpanEvent);
            assertThat(result.getAttributes().size(), equalTo(1));
            assertThat(result.getDroppedAttributesCount(), equalTo(0));
            assertThat(result.getName(), equalTo(testName));
            assertThat(result.getTime(), equalTo(testTime));
        }

        @Test
        public void testGetSpanLink() {
            final byte[] testSpanIdBytes = getRandomBytes(16);
            final byte[] testTraceIdBytes = getRandomBytes(16);
            final String testSpanId = Hex.encodeHexString(testSpanIdBytes);
            final String testTraceId = Hex.encodeHexString(testTraceIdBytes);
            final String testTraceState = "test state";
            final String testKey = "test key";
            final String testValue = "test value";
            io.opentelemetry.proto.trace.v1.Span.Link testOTelProtoSpanLink = io.opentelemetry.proto.trace.v1.Span.Link.newBuilder()
                    .setSpanId(ByteString.copyFrom(testSpanIdBytes))
                    .setTraceId(ByteString.copyFrom(testTraceIdBytes))
                    .setTraceState(testTraceState)
                    .setDroppedAttributesCount(0)
                    .addAttributes(KeyValue.newBuilder().setKey(testKey).setValue(AnyValue.newBuilder()
                            .setStringValue(testValue).build()).build())
                    .build();
            final Link result = decoderUnderTest.getLink(testOTelProtoSpanLink);
            assertThat(result.getAttributes().size(), equalTo(1));
            assertThat(result.getDroppedAttributesCount(), equalTo(0));
            assertThat(result.getSpanId(), equalTo(testSpanId));
            assertThat(result.getTraceId(), equalTo(testTraceId));
            assertThat(result.getTraceState(), equalTo(testTraceState));
        }

        /**
         * Below object has a KeyValue with a key mapped to KeyValueList and is part of the span attributes
         *
         * @throws JsonProcessingException
         */
        @Test
        public void testKeyValueListAsSpanAttributes() throws JsonProcessingException {

            final KeyValue childAttr1 = KeyValue.newBuilder().setKey("statement").setValue(AnyValue.newBuilder()
                    .setIntValue(1_000).build()).build();
            final KeyValue childAttr2 = KeyValue.newBuilder().setKey("statement.params").setValue(AnyValue.newBuilder()
                    .setStringValue("us-east-1").build()).build();
            final KeyValue spanAttribute1 = KeyValue.newBuilder().setKey("db.details").setValue(AnyValue.newBuilder()
                    .setKvlistValue(KeyValueList.newBuilder().addAllValues(Arrays.asList(childAttr1, childAttr2)).build()).build()).build();
            final KeyValue spanAttribute2 = KeyValue.newBuilder().setKey("http.status").setValue(AnyValue.newBuilder()
                    .setStringValue("4xx").build()).build();

            final Map<String, Object> actual = decoderUnderTest.getSpanAttributes(io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .addAllAttributes(Arrays.asList(spanAttribute1, spanAttribute2)).build());
            assertThat(actual.get(OTelProtoCodec.SPAN_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute2.getKey())),
                    equalTo(spanAttribute2.getValue().getStringValue()));
            assertThat(actual.containsKey(OTelProtoCodec.SPAN_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute1.getKey())), is(true));
            final Map<String, Object> actualValue = returnMap((String) actual
                    .get(OTelProtoCodec.SPAN_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute1.getKey())));
            assertThat(((Number) actualValue.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr1.getKey()))).longValue(),
                    equalTo(childAttr1.getValue().getIntValue()));
            assertThat(actualValue.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr2.getKey())), equalTo(childAttr2.getValue().getStringValue()));
        }

        /**
         * Below object has a KeyValue with a key mapped to KeyValueList and is part of the resource attributes
         *
         * @throws JsonProcessingException
         */
        @Test
        public void testKeyValueListAsResourceAttributes() throws JsonProcessingException {
            final KeyValue childAttr1 = KeyValue.newBuilder().setKey("ec2.instances").setValue(AnyValue.newBuilder()
                    .setIntValue(20).build()).build();
            final KeyValue childAttr2 = KeyValue.newBuilder().setKey("ec2.instance.az").setValue(AnyValue.newBuilder()
                    .setStringValue("us-east-1").build()).build();
            final KeyValue spanAttribute1 = KeyValue.newBuilder().setKey("aws.details").setValue(AnyValue.newBuilder()
                    .setKvlistValue(KeyValueList.newBuilder().addAllValues(Arrays.asList(childAttr1, childAttr2)).build()).build()).build();
            final KeyValue spanAttribute2 = KeyValue.newBuilder().setKey("service.name").setValue(AnyValue.newBuilder()
                    .setStringValue("EaglesService").build()).build();

            final Map<String, Object> actual = decoderUnderTest.getResourceAttributes(Resource.newBuilder()
                    .addAllAttributes(Arrays.asList(spanAttribute1, spanAttribute2)).build());
            assertThat(actual.get(OTelProtoCodec.RESOURCE_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute2.getKey())),
                    equalTo(spanAttribute2.getValue().getStringValue()));
            assertThat(actual.containsKey(OTelProtoCodec.RESOURCE_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute1.getKey())), is(true));
            final Map<String, Object> actualValue = returnMap((String) actual
                    .get(OTelProtoCodec.RESOURCE_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute1.getKey())));
            assertThat(((Number) actualValue.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr1.getKey()))).longValue(), equalTo(childAttr1.getValue().getIntValue()));
            assertThat(actualValue.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr2.getKey())), equalTo(childAttr2.getValue().getStringValue()));

        }


        /**
         * Below object has a KeyValue with a key mapped to KeyValueList and is part of the span attributes
         *
         * @throws JsonProcessingException
         */
        @Test
        public void testArrayOfValueAsResourceAttributes() throws JsonProcessingException {
            final KeyValue childAttr1 = KeyValue.newBuilder().setKey("ec2.instances").setValue(AnyValue.newBuilder()
                    .setIntValue(20).build()).build();
            final KeyValue childAttr2 = KeyValue.newBuilder().setKey("ec2.instance.az").setValue(AnyValue.newBuilder()
                    .setStringValue("us-east-1").build()).build();
            final AnyValue anyValue1 = AnyValue.newBuilder().setStringValue(UUID.randomUUID().toString()).build();
            final AnyValue anyValue2 = AnyValue.newBuilder().setDoubleValue(2000.123).build();
            final AnyValue anyValue3 = AnyValue.newBuilder().setKvlistValue(KeyValueList.newBuilder().addAllValues(Arrays.asList(childAttr1, childAttr2))).build();
            final ArrayValue arrayValue = ArrayValue.newBuilder().addAllValues(Arrays.asList(anyValue1, anyValue2, anyValue3)).build();
            final KeyValue spanAttribute1 = KeyValue.newBuilder().setKey("aws.details").setValue(AnyValue.newBuilder()
                    .setArrayValue(arrayValue)).build();

            final Map<String, Object> actual = decoderUnderTest.getResourceAttributes(Resource.newBuilder()
                    .addAllAttributes(Collections.singletonList(spanAttribute1)).build());
            assertThat(actual.containsKey(OTelProtoCodec.RESOURCE_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute1.getKey())), is(true));
            final List<Object> actualValue = returnList((String) actual
                    .get(OTelProtoCodec.RESOURCE_ATTRIBUTES_REPLACE_DOT_WITH_AT.apply(spanAttribute1.getKey())));
            assertThat(actualValue.get(0), equalTo(anyValue1.getStringValue()));
            assertThat(((Double) actualValue.get(1)), equalTo(anyValue2.getDoubleValue()));
            final Map<String, Object> map = returnMap((String) actualValue.get(2));
            assertThat(((Number) map.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr1.getKey()))).longValue(), equalTo(childAttr1.getValue().getIntValue()));
            assertThat(map.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr2.getKey())), equalTo(childAttr2.getValue().getStringValue()));
            assertThat(((Number) map.get(OTelProtoCodec.REPLACE_DOT_WITH_AT.apply(childAttr1.getKey()))).longValue(), equalTo(childAttr1.getValue().getIntValue()));

        }


        @Test
        public void testInstrumentationLibraryAttributes() {
            final InstrumentationLibrary il1 = InstrumentationLibrary.newBuilder().setName("Jaeger").setVersion("0.6.0").build();
            final InstrumentationLibrary il2 = InstrumentationLibrary.newBuilder().setName("Jaeger").build();
            final InstrumentationLibrary il3 = InstrumentationLibrary.newBuilder().setVersion("0.6.0").build();
            final InstrumentationLibrary il4 = InstrumentationLibrary.newBuilder().build();

            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il1).size(), equalTo(2));
            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il1).get(OTelProtoCodec.INSTRUMENTATION_LIBRARY_NAME), equalTo(il1.getName()));
            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il1).get(OTelProtoCodec.INSTRUMENTATION_LIBRARY_VERSION), equalTo(il1.getVersion()));

            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il2).size(), equalTo(1));
            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il2).get(OTelProtoCodec.INSTRUMENTATION_LIBRARY_NAME), equalTo(il2.getName()));

            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il3).size(), equalTo(1));
            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il3).get(OTelProtoCodec.INSTRUMENTATION_LIBRARY_VERSION), equalTo(il3.getVersion()));

            assertThat(decoderUnderTest.getInstrumentationLibraryAttributes(il4).isEmpty(), is(true));
        }

        @Test
        public void testStatusAttributes() {
            final Status st1 = Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).setMessage("Some message").build();
            final Status st2 = Status.newBuilder().setMessage("error message").build();
            final Status st3 = Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_UNSET).build();
            final Status st4 = Status.newBuilder().build();

            assertThat(decoderUnderTest.getSpanStatusAttributes(st1).size(), equalTo(2));
            assertThat(Status.StatusCode.forNumber((Integer) decoderUnderTest.getSpanStatusAttributes(st1).get(OTelProtoCodec.STATUS_CODE)), equalTo(st1.getCode()));
            assertThat(decoderUnderTest.getSpanStatusAttributes(st1).get(OTelProtoCodec.STATUS_MESSAGE), equalTo(st1.getMessage()));

            assertThat(decoderUnderTest.getSpanStatusAttributes(st2).size(), equalTo(2));
            assertThat(Status.StatusCode.forNumber((Integer) decoderUnderTest.getSpanStatusAttributes(st2).get(OTelProtoCodec.STATUS_CODE)), equalTo(st2.getCode()));

            assertThat(decoderUnderTest.getSpanStatusAttributes(st3).size(), equalTo(1));
            assertThat(Status.StatusCode.forNumber((Integer) decoderUnderTest.getSpanStatusAttributes(st3).get(OTelProtoCodec.STATUS_CODE)), equalTo(st3.getCode()));

            assertThat(decoderUnderTest.getSpanStatusAttributes(st4).size(), equalTo(1));
            assertThat(Status.StatusCode.forNumber((Integer) decoderUnderTest.getSpanStatusAttributes(st4).get(OTelProtoCodec.STATUS_CODE)), equalTo(st4.getCode()));

        }

        @Test
        public void testISO8601() {
            final long NANO_MULTIPLIER = 1_000 * 1_000 * 1_000;
            final io.opentelemetry.proto.trace.v1.Span startTimeUnixNano = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .setStartTimeUnixNano(651242400000000321L).build();
            final io.opentelemetry.proto.trace.v1.Span endTimeUnixNano = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .setEndTimeUnixNano(1598013600000000321L).build();
            final io.opentelemetry.proto.trace.v1.Span emptyTimeSpan = io.opentelemetry.proto.trace.v1.Span.newBuilder().build();

            final String startTime = decoderUnderTest.getStartTimeISO8601(startTimeUnixNano);
            assertThat(Instant.parse(startTime).getEpochSecond() * NANO_MULTIPLIER + Instant.parse(startTime).getNano(), equalTo(startTimeUnixNano.getStartTimeUnixNano()));
            final String endTime = decoderUnderTest.getEndTimeISO8601(endTimeUnixNano);
            assertThat(Instant.parse(endTime).getEpochSecond() * NANO_MULTIPLIER + Instant.parse(endTime).getNano(), equalTo(endTimeUnixNano.getEndTimeUnixNano()));
            final String emptyTime = decoderUnderTest.getStartTimeISO8601(endTimeUnixNano);
            assertThat(Instant.parse(emptyTime).getEpochSecond() * NANO_MULTIPLIER + Instant.parse(emptyTime).getNano(), equalTo(emptyTimeSpan.getStartTimeUnixNano()));

        }

        @Test
        public void testTraceGroup() {
            final io.opentelemetry.proto.trace.v1.Span span1 = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .setParentSpanId(ByteString.copyFrom("PArentIdExists", StandardCharsets.UTF_8)).build();
            assertThat(decoderUnderTest.getTraceGroup(span1), nullValue());
            final String testTraceGroup = "testTraceGroup";
            final io.opentelemetry.proto.trace.v1.Span span2 = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .setName(testTraceGroup).build();
            assertThat(decoderUnderTest.getTraceGroup(span2), equalTo(testTraceGroup));
        }

        @Test
        public void testTraceGroupFields() {
            final io.opentelemetry.proto.trace.v1.Span span1 = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .setParentSpanId(ByteString.copyFrom("PArentIdExists", StandardCharsets.UTF_8)).build();
            final TraceGroupFields traceGroupFields1 = decoderUnderTest.getTraceGroupFields(span1);
            assertThat(traceGroupFields1.getEndTime(), nullValue());
            assertThat(traceGroupFields1.getDurationInNanos(), nullValue());
            assertThat(traceGroupFields1.getStatusCode(), nullValue());
            final long testStartTimeUnixNano = 100;
            final long testEndTimeUnixNano = 100;
            final int testStatusCode = Status.StatusCode.STATUS_CODE_OK.getNumber();
            final io.opentelemetry.proto.trace.v1.Span span2 = io.opentelemetry.proto.trace.v1.Span.newBuilder()
                    .setStartTimeUnixNano(testStartTimeUnixNano)
                    .setEndTimeUnixNano(testEndTimeUnixNano)
                    .setStatus(Status.newBuilder().setCodeValue(testStatusCode))
                    .build();
            final TraceGroupFields expectedTraceGroupFields = DefaultTraceGroupFields.builder()
                    .withStatusCode(testStatusCode)
                    .withEndTime(decoderUnderTest.getEndTimeISO8601(span2))
                    .withDurationInNanos(testEndTimeUnixNano - testStartTimeUnixNano)
                    .build();
            assertThat(decoderUnderTest.getTraceGroupFields(span2), equalTo(expectedTraceGroupFields));
        }
    }

    @Nested
    class OTelProtoEncoderTest {
        @Test
        public void testNullToAnyValue() throws UnsupportedEncodingException {
            final AnyValue result = encoderUnderTest.objectToAnyValue(null);
            assertThat(result.getValueCase(), equalTo(AnyValue.ValueCase.VALUE_NOT_SET));
        }

        @Test
        public void testIntegerToAnyValue() throws UnsupportedEncodingException {
            final Integer testInteger = 1;
            final AnyValue result = encoderUnderTest.objectToAnyValue(testInteger);
            assertThat(result.getValueCase(), equalTo(AnyValue.ValueCase.INT_VALUE));
            assertThat(result.getIntValue(), equalTo(testInteger.longValue()));
        }

        @Test
        public void testLongToAnyValue() throws UnsupportedEncodingException {
            final Long testLong = 1L;
            final AnyValue result = encoderUnderTest.objectToAnyValue(testLong);
            assertThat(result.getValueCase(), equalTo(AnyValue.ValueCase.INT_VALUE));
            assertThat(result.getIntValue(), equalTo(testLong));
        }

        @Test
        public void testBooleanToAnyValue() throws UnsupportedEncodingException {
            final Boolean testBoolean = false;
            final AnyValue result = encoderUnderTest.objectToAnyValue(testBoolean);
            assertThat(result.getValueCase(), equalTo(AnyValue.ValueCase.BOOL_VALUE));
            assertThat(result.getBoolValue(), is(testBoolean));
        }

        @Test
        public void testStringToAnyValue() throws UnsupportedEncodingException {
            final String testString = "test string";
            final AnyValue result = encoderUnderTest.objectToAnyValue(testString);
            assertThat(result.getValueCase(), equalTo(AnyValue.ValueCase.STRING_VALUE));
            assertThat(result.getStringValue(), equalTo(testString));
        }

        @Test
        public void testUnsupportedTypeToAnyValue() {
            assertThrows(UnsupportedEncodingException.class,
                    () -> encoderUnderTest.objectToAnyValue(new UnsupportedEncodingClass()));
        }

        @Test
        public void testSpanAttributesToKeyValueList() throws UnsupportedEncodingException {
            final String testKeyRelevant = "relevantKey";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.OTelProtoEncoder.SPAN_ATTRIBUTES_PREFIX + testKeyRelevant, 1,
                    testKeyIrrelevant, 2);
            final List<KeyValue> result = encoderUnderTest.getSpanAttributes(testAllAttributes);
            assertThat(result.size(), equalTo(1));
            assertThat(result.get(0).getKey(), equalTo(testKeyRelevant));
            assertThat(result.get(0).getValue().getIntValue(), equalTo(1L));
        }

        @Test
        public void testResourceAttributesToKeyValueList() throws UnsupportedEncodingException {
            final String testKeyRelevant = "relevantKey";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.OTelProtoEncoder.RESOURCE_ATTRIBUTES_PREFIX + testKeyRelevant, 1,
                    OTelProtoCodec.OTelProtoEncoder.RESOURCE_ATTRIBUTES_PREFIX + OTelProtoCodec.OTelProtoEncoder.SERVICE_NAME_ATTRIBUTE, "A",
                    testKeyIrrelevant, 2);
            final List<KeyValue> result = encoderUnderTest.getResourceAttributes(testAllAttributes);
            assertThat(result.size(), equalTo(1));
            assertThat(result.get(0).getKey(), equalTo(testKeyRelevant));
            assertThat(result.get(0).getValue().getIntValue(), equalTo(1L));
        }

        @Test
        public void testEncodeSpanStatusComplete() {
            final String testStatusMessage = "test message";
            final int testStatusCode = Status.StatusCode.STATUS_CODE_OK.getNumber();
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.STATUS_CODE, testStatusCode,
                    OTelProtoCodec.STATUS_MESSAGE, testStatusMessage,
                    testKeyIrrelevant, 2);
            final Status status = encoderUnderTest.constructSpanStatus(testAllAttributes);
            assertThat(status.getCodeValue(), equalTo(testStatusCode));
            assertThat(status.getMessage(), equalTo(testStatusMessage));
        }

        @Test
        public void testEncodeSpanStatusMissingStatusMessage() {
            final int testStatusCode = Status.StatusCode.STATUS_CODE_OK.getNumber();
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.STATUS_CODE, testStatusCode,
                    testKeyIrrelevant, 2);
            final Status status = encoderUnderTest.constructSpanStatus(testAllAttributes);
            assertThat(status.getCodeValue(), equalTo(testStatusCode));
        }

        @Test
        public void testEncodeSpanStatusMissingStatusCode() {
            final String testStatusMessage = "test message";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.STATUS_MESSAGE, testStatusMessage,
                    testKeyIrrelevant, 2);
            final Status status = encoderUnderTest.constructSpanStatus(testAllAttributes);
            assertThat(status.getMessage(), equalTo(testStatusMessage));
        }

        @Test
        public void testEncodeSpanStatusMissingAll() {
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(testKeyIrrelevant, 2);
            final Status status = encoderUnderTest.constructSpanStatus(testAllAttributes);
            assertThat(status, instanceOf(Status.class));
        }

        @Test
        public void testEncodeInstrumentationLibraryComplete() {
            final String testName = "test name";
            final String testVersion = "1.1";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.INSTRUMENTATION_LIBRARY_NAME, testName,
                    OTelProtoCodec.INSTRUMENTATION_LIBRARY_VERSION, testVersion,
                    testKeyIrrelevant, 2);
            final InstrumentationLibrary instrumentationLibrary = encoderUnderTest.constructInstrumentationLibrary(testAllAttributes);
            assertThat(instrumentationLibrary.getName(), equalTo(testName));
            assertThat(instrumentationLibrary.getVersion(), equalTo(testVersion));
        }

        @Test
        public void testEncodeInstrumentationLibraryMissingName() {
            final String testVersion = "1.1";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.INSTRUMENTATION_LIBRARY_VERSION, testVersion,
                    testKeyIrrelevant, 2);
            final InstrumentationLibrary instrumentationLibrary = encoderUnderTest.constructInstrumentationLibrary(testAllAttributes);
            assertThat(instrumentationLibrary.getVersion(), equalTo(testVersion));
        }

        @Test
        public void testEncodeInstrumentationLibraryMissingVersion() {
            final String testName = "test name";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.INSTRUMENTATION_LIBRARY_NAME, testName,
                    testKeyIrrelevant, 2);
            final InstrumentationLibrary instrumentationLibrary = encoderUnderTest.constructInstrumentationLibrary(testAllAttributes);
            assertThat(instrumentationLibrary.getName(), equalTo(testName));
        }

        @Test
        public void testEncodeInstrumentationLibraryMissingAll() {
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(testKeyIrrelevant, 2);
            final InstrumentationLibrary instrumentationLibrary = encoderUnderTest.constructInstrumentationLibrary(testAllAttributes);
            assertThat(instrumentationLibrary, instanceOf(InstrumentationLibrary.class));
        }

        @Test
        public void testEncodeResourceComplete() throws UnsupportedEncodingException {
            final String testServiceName = "test name";
            final String testKeyRelevant = "relevantKey";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.OTelProtoEncoder.RESOURCE_ATTRIBUTES_PREFIX + testKeyRelevant, 1,
                    OTelProtoCodec.OTelProtoEncoder.RESOURCE_ATTRIBUTES_PREFIX + OTelProtoCodec.OTelProtoEncoder.SERVICE_NAME_ATTRIBUTE, "A",
                    testKeyIrrelevant, 2);
            final Resource resource = encoderUnderTest.constructResource(testServiceName, testAllAttributes);
            assertThat(resource.getAttributesCount(), equalTo(2));
            assertThat(
                    resource.getAttributesList().stream()
                            .anyMatch(kv -> kv.getKey().equals(OTelProtoCodec.SERVICE_NAME) && kv.getValue().getStringValue().equals(testServiceName)),
                    is(true));
            assertThat(resource.getAttributesList().stream().noneMatch(kv -> kv.getKey().equals(OTelProtoCodec.OTelProtoEncoder.SERVICE_NAME_ATTRIBUTE)), is(true));
        }

        @Test
        public void testEncodeResourceMissingServiceName() throws UnsupportedEncodingException {
            final String testKeyRelevant = "relevantKey";
            final String testKeyIrrelevant = "irrelevantKey";
            final Map<String, Object> testAllAttributes = Map.of(
                    OTelProtoCodec.OTelProtoEncoder.RESOURCE_ATTRIBUTES_PREFIX + testKeyRelevant, 1,
                    testKeyIrrelevant, 2);
            final Resource resource = encoderUnderTest.constructResource(null, testAllAttributes);
            assertThat(resource.getAttributesCount(), equalTo(1));
            assertThat(resource.getAttributesList().stream().noneMatch(kv -> kv.getKey().equals(OTelProtoCodec.SERVICE_NAME)), is(true));
        }

        @Test
        public void testEncodeSpanEvent() throws UnsupportedEncodingException {
            final String testName = "test name";
            final long testTimeNanos = System.nanoTime();
            final String testTime = OTelProtoCodec.convertUnixNanosToISO8601(testTimeNanos);
            final String testKey = "test key";
            final String testValue = "test value";
            final SpanEvent testSpanEvent = DefaultSpanEvent.builder()
                    .withName(testName)
                    .withDroppedAttributesCount(0)
                    .withTime(testTime)
                    .withAttributes(Map.of(testKey, testValue))
                    .build();
            final io.opentelemetry.proto.trace.v1.Span.Event result = encoderUnderTest.convertSpanEvent(testSpanEvent);
            assertThat(result.getAttributesCount(), equalTo(1));
            assertThat(result.getDroppedAttributesCount(), equalTo(0));
            assertThat(result.getName(), equalTo(testName));
            assertThat(result.getTimeUnixNano(), equalTo(testTimeNanos));
        }

        @Test
        public void testEncodeSpanLink() throws DecoderException, UnsupportedEncodingException {
            final byte[] testSpanIdBytes = getRandomBytes(16);
            final byte[] testTraceIdBytes = getRandomBytes(16);
            final String testSpanId = Hex.encodeHexString(testSpanIdBytes);
            final String testTraceId = Hex.encodeHexString(testTraceIdBytes);
            final String testTraceState = "test state";
            final String testKey = "test key";
            final String testValue = "test value";
            final Link testSpanLink = DefaultLink.builder()
                    .withSpanId(testSpanId)
                    .withTraceId(testTraceId)
                    .withTraceState(testTraceState)
                    .withDroppedAttributesCount(0)
                    .withAttributes(Map.of(testKey, testValue))
                    .build();
            final io.opentelemetry.proto.trace.v1.Span.Link result = encoderUnderTest.convertSpanLink(testSpanLink);
            assertThat(result.getAttributesCount(), equalTo(1));
            assertThat(result.getDroppedAttributesCount(), equalTo(0));
            assertThat(result.getSpanId().toByteArray(), equalTo(testSpanIdBytes));
            assertThat(result.getTraceId().toByteArray(), equalTo(testTraceIdBytes));
            assertThat(result.getTraceState(), equalTo(testTraceState));
        }

        @Test
        public void testEncodeResourceSpans() throws DecoderException, UnsupportedEncodingException {
            final Span testSpan = buildSpanFromJsonFile(TEST_SPAN_EVENT_JSON_FILE);
            final ResourceSpans rs = encoderUnderTest.convertToResourceSpans(testSpan);
            assertThat(rs.getResource(), equalTo(Resource.getDefaultInstance()));
            assertThat(rs.getInstrumentationLibrarySpansCount(), equalTo(1));
            final InstrumentationLibrarySpans instrumentationLibrarySpans = rs.getInstrumentationLibrarySpans(0);
            assertThat(instrumentationLibrarySpans.getInstrumentationLibrary(), equalTo(InstrumentationLibrary.getDefaultInstance()));
            assertThat(instrumentationLibrarySpans.getSpansCount(), equalTo(1));
            final io.opentelemetry.proto.trace.v1.Span otelProtoSpan = instrumentationLibrarySpans.getSpans(0);
            assertThat(otelProtoSpan.getTraceId(), equalTo(ByteString.copyFrom(Hex.decodeHex(testSpan.getTraceId()))));
            assertThat(otelProtoSpan.getSpanId(), equalTo(ByteString.copyFrom(Hex.decodeHex(testSpan.getSpanId()))));
            assertThat(otelProtoSpan.getParentSpanId(), equalTo(ByteString.copyFrom(Hex.decodeHex(testSpan.getParentSpanId()))));
            assertThat(otelProtoSpan.getName(), equalTo(testSpan.getName()));
            assertThat(otelProtoSpan.getKind(), equalTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.valueOf(testSpan.getKind())));
            assertThat(otelProtoSpan.getTraceState(), equalTo(testSpan.getTraceState()));
            assertThat(otelProtoSpan.getEventsCount(), equalTo(0));
            assertThat(otelProtoSpan.getDroppedEventsCount(), equalTo(0));
            assertThat(otelProtoSpan.getLinksCount(), equalTo(0));
            assertThat(otelProtoSpan.getDroppedLinksCount(), equalTo(0));
            assertThat(otelProtoSpan.getAttributesCount(), equalTo(0));
            assertThat(otelProtoSpan.getDroppedAttributesCount(), equalTo(0));
        }

        private Span buildSpanFromJsonFile(final String jsonFileName) {
            JacksonSpan.Builder spanBuilder = JacksonSpan.builder();
            try (final InputStream inputStream = Objects.requireNonNull(
                    OTelProtoCodecTest.class.getClassLoader().getResourceAsStream(jsonFileName))){
                final Map<String, Object> spanMap = OBJECT_MAPPER.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
                final String traceId = (String) spanMap.get("traceId");
                final String spanId = (String) spanMap.get("spanId");
                final String parentSpanId = (String) spanMap.get("parentSpanId");
                final String traceState = (String) spanMap.get("traceState");
                final String name = (String) spanMap.get("name");
                final String kind = (String) spanMap.get("kind");
                final Long durationInNanos = ((Number) spanMap.get("durationInNanos")).longValue();
                final String startTime = (String) spanMap.get("startTime");
                final String endTime = (String) spanMap.get("endTime");
                spanBuilder = spanBuilder
                        .withTraceId(traceId)
                        .withSpanId(spanId)
                        .withParentSpanId(parentSpanId)
                        .withTraceState(traceState)
                        .withName(name)
                        .withKind(kind)
                        .withDurationInNanos(durationInNanos)
                        .withStartTime(startTime)
                        .withEndTime(endTime)
                        .withTraceGroup(null);
                DefaultTraceGroupFields.Builder traceGroupFieldsBuilder = DefaultTraceGroupFields.builder();
                if (parentSpanId.isEmpty()) {
                    final Integer statusCode = (Integer) ((Map<String, Object>) spanMap.get("traceGroupFields")).get("statusCode");
                    traceGroupFieldsBuilder = traceGroupFieldsBuilder
                            .withStatusCode(statusCode)
                            .withEndTime(endTime)
                            .withDurationInNanos(durationInNanos);
                    final String traceGroup = (String) spanMap.get("traceGroup");
                    spanBuilder = spanBuilder.withTraceGroup(traceGroup);
                }
                spanBuilder.withTraceGroupFields(traceGroupFieldsBuilder.build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return spanBuilder.build();
        }

        private class UnsupportedEncodingClass { }
    }

    @Test
    public void testTimeCodec() {
        final long testNanos = System.nanoTime();
        final String timeISO8601 = OTelProtoCodec.convertUnixNanosToISO8601(testNanos);
        final long nanoCodecResult = OTelProtoCodec.timeISO8601ToNanos(OTelProtoCodec.convertUnixNanosToISO8601(testNanos));
        assertThat(nanoCodecResult, equalTo(testNanos));
        final String stringCodecResult = OTelProtoCodec.convertUnixNanosToISO8601(OTelProtoCodec.timeISO8601ToNanos(timeISO8601));
        assertThat(stringCodecResult, equalTo(timeISO8601));
    }

    @Test
    public void testOTelProtoCodecConsistency() throws IOException, DecoderException {
        final ExportTraceServiceRequest request = buildExportTraceServiceRequestFromJsonFile(TEST_REQUEST_JSON_FILE);
        final List<Span> spansFirstDec = decoderUnderTest.parseExportTraceServiceRequest(request);
        final List<ResourceSpans> resourceSpansList = new ArrayList<>();
        for (final Span span: spansFirstDec) {
            resourceSpansList.add(encoderUnderTest.convertToResourceSpans(span));
        }
        final List<Span> spansSecondDec = resourceSpansList.stream()
                .flatMap(rs -> decoderUnderTest.parseResourceSpans(rs).stream()).collect(Collectors.toList());
        assertThat(spansFirstDec.size(), equalTo(spansSecondDec.size()));
        for (int i = 0; i < spansFirstDec.size(); i++) {
            assertThat(spansFirstDec.get(i).toJsonString(), equalTo(spansSecondDec.get(i).toJsonString()));
        }
    }
}
