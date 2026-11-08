package com.awesome.testing.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.IntStream;
import jakarta.jms.Connection;
import jakarta.jms.QueueConnection;
import jakarta.jms.QueueSession;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicSession;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQSession;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jms.connection.SessionProxy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JVM-side guard for the GraalVM native image metadata. The native binary itself is exercised by
 * {@code scripts/native-smoke.sh}; these checks catch dependency upgrades that add classes the curated
 * metadata does not know about (a new OpenWire version or a renamed transport factory) without a native build.
 */
class NativeImageHintsTest {

    private static final String METADATA =
            "META-INF/native-image/com.awesome.testing/consumer-library-hints/reachability-metadata.json";

    @Test
    void registersTheEmailContractForJacksonBindingAndTypeIdLookup() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new EmailContractRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(EmailDto.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(EmailTemplate.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onConstructorInvocation(EmailDto.class.getDeclaredConstructor())).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(EmailDto.class, "setTemplate")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(EmailDto.class, "getProperties")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(EmailTemplate.class.getMethod("fromValue", String.class))).accepts(hints);
    }

    /** Spring AOT writes its generated metadata to com.awesome.testing/consumer and would overwrite a file there. */
    @Test
    void keepsCuratedMetadataOutOfTheSpringAotOutputDirectory() {
        assertThat(Path.of("src/main/resources/META-INF/native-image/com.awesome.testing/consumer")).doesNotExist();
        assertThat(new ClassPathResource(METADATA).exists()).isTrue();
    }

    @Test
    void jmsConfigImportsTheContractHints() {
        assertThat(JmsConfig.class.getAnnotation(org.springframework.context.annotation.ImportRuntimeHints.class)
                .value()).containsExactly(EmailContractRuntimeHints.class);
    }

    @Test
    void registersEveryOpenWireMarshallerFactoryOnTheClasspath() throws Exception {
        Set<String> registered = registeredTypes();
        List<String> marshallerFactories = IntStream.rangeClosed(1, 30)
                .mapToObj(version -> "org.apache.activemq.openwire.v" + version + ".MarshallerFactory")
                .filter(NativeImageHintsTest::isPresent)
                // v1 is a legacy protocol no supported broker negotiates; the client starts at the current version.
                .filter(name -> !name.contains(".v1."))
                .toList();

        assertThat(marshallerFactories).isNotEmpty();
        assertThat(registered).containsAll(marshallerFactories);
    }

    @Test
    void registersTheActiveMqFactoryFinderEntriesTheConsumerUses() throws Exception {
        Set<String> registered = registeredTypes();
        Set<String> resources = registeredResources();

        for (String path : List.of("transport/tcp", "transport/ssl", "transport/nio", "transport/nio+ssl",
                "transport/failover", "wireformat/default")) {
            String resource = "META-INF/services/org/apache/activemq/" + path;
            assertThat(resources).as("resource hint for %s", resource).contains(resource);
            assertThat(registered).as("factory class named by %s", resource).contains(factoryClass(resource));
        }
    }

    /**
     * Mirrors {@code SingleConnectionFactory#getSharedConnectionProxy} and
     * {@code CachingConnectionFactory#getCachedSessionProxy}: the JDK proxy interfaces depend on which queue/topic
     * variants the ActiveMQ client objects implement, and a native image can only create pre-registered proxies.
     */
    @Test
    void registersTheSpringJmsProxiesForTheActiveMqClientShapes() throws Exception {
        List<String> connectionProxy = new ArrayList<>(List.of(Connection.class.getName()));
        if (QueueConnection.class.isAssignableFrom(ActiveMQConnection.class)) {
            connectionProxy.add(QueueConnection.class.getName());
        }
        if (TopicConnection.class.isAssignableFrom(ActiveMQConnection.class)) {
            connectionProxy.add(TopicConnection.class.getName());
        }
        List<String> sessionProxy = new ArrayList<>(List.of(SessionProxy.class.getName()));
        if (QueueSession.class.isAssignableFrom(ActiveMQSession.class)) {
            sessionProxy.add(QueueSession.class.getName());
        }
        if (TopicSession.class.isAssignableFrom(ActiveMQSession.class)) {
            sessionProxy.add(TopicSession.class.getName());
        }

        assertThat(registeredProxies()).contains(connectionProxy, sessionProxy);
    }

    @Test
    void everyReflectionEntryNamesAClassThatExists() throws Exception {
        assertThat(registeredTypes()).allSatisfy(type -> assertThat(isPresent(type)).as(type).isTrue());
    }

    @Test
    void everyResourceEntryNamesAResourceThatExists() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String resource : registeredResources()) {
            Resource[] matches = resolver.getResources("classpath*:" + resource);
            assertThat(matches).as(resource).isNotEmpty();
        }
    }

    private static Set<String> registeredTypes() throws IOException {
        Set<String> types = new HashSet<>();
        metadata().get("reflection").forEach(entry -> {
            if (entry.get("type").isString()) {
                types.add(entry.get("type").asString());
            }
        });
        return types;
    }

    private static Set<List<String>> registeredProxies() throws IOException {
        Set<List<String>> proxies = new HashSet<>();
        metadata().get("reflection").forEach(entry -> {
            JsonNode proxy = entry.get("type").get("proxy");
            if (proxy != null) {
                proxies.add(proxy.valueStream().map(JsonNode::asString).toList());
            }
        });
        return proxies;
    }

    private static Set<String> registeredResources() throws IOException {
        Set<String> globs = new HashSet<>();
        metadata().get("resources").forEach(entry -> globs.add(entry.get("glob").asString()));
        return globs;
    }

    private static JsonNode metadata() throws IOException {
        return JsonMapper.builder().build()
                .readTree(new ClassPathResource(METADATA).getContentAsString(StandardCharsets.UTF_8));
    }

    private static String factoryClass(String resource) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("class");
        }
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, NativeImageHintsTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
