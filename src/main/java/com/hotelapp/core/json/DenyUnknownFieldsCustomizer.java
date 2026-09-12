package com.hotelapp.core.json;

import java.util.List;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Wires {@link DenyUnknownFields} into the application {@code JsonMapper}: an
 * unknown property on a marked DTO throws {@link UnrecognizedPropertyException}
 * (matching serde's {@code deny_unknown_fields}), while every other DTO keeps
 * the serde default of tolerating extra fields.
 */
@Component
public class DenyUnknownFieldsCustomizer implements JsonMapperBuilderCustomizer {

    public static final DeserializationProblemHandler HANDLER = new DeserializationProblemHandler() {
        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt,
                tools.jackson.core.JsonParser parser, ValueDeserializer<?> deserializer,
                Object beanOrClass, String propertyName) {
            Class<?> beanClass = beanOrClass instanceof Class<?> clazz
                    ? clazz : beanOrClass.getClass();
            if (beanClass.isAnnotationPresent(DenyUnknownFields.class)) {
                throw UnrecognizedPropertyException.from(
                        parser, beanOrClass, propertyName, List.of());
            }
            return true;
        }
    };

    @Override
    public void customize(tools.jackson.databind.json.JsonMapper.Builder builder) {
        builder.addHandler(HANDLER);
    }
}
