package com.awesome.testing.listener;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image hints for the JMS email contract.
 *
 * <p>{@link JmsConfig}'s converter binds the JSON payload to {@link EmailDto} with Jackson 3. Besides the explicit
 * {@code _awesome_} type-id mappings, the producer sends the fully qualified class name
 * {@code com.awesome.testing.dto.email.EmailDto}, which the converter resolves through {@code Class.forName}; a
 * native image only finds that class by name when it is registered for reflection. Jackson also needs the creator,
 * accessors, enum constants and the {@code EmailTemplate.fromValue} {@code @JsonCreator} of the payload types (Lombok accessors are public, and Jackson 3 may bind through the
 * all-arguments constructor using {@code -parameters} names). Library internals (ActiveMQ OpenWire, Angus Mail) are covered by
 * {@code META-INF/native-image/com.awesome.testing/consumer-library-hints/reachability-metadata.json}.
 */
class EmailContractRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
                .registerType(EmailDto.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(EmailTemplate.class, type -> type
                        .withMembers(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_PUBLIC_FIELDS)
                        // @JsonCreator factory mapping unknown template names to GENERIC; Jackson invokes it
                        // reflectively, so it is named explicitly rather than relying on the public-method category.
                        .withMethod("fromValue", List.of(TypeReference.of(String.class)), ExecutableMode.INVOKE));
    }
}
