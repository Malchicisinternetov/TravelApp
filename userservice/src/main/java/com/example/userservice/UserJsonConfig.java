package com.example.userservice;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import java.util.Currency;

@Configuration
public class UserJsonConfig {
    @Bean
    public JsonMapperBuilderCustomizer currencyJsonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Currency.class, ToStringSerializer.instance);
            module.addDeserializer(Currency.class, new ValueDeserializer<Currency>() {
                @Override
                public Currency deserialize(JsonParser parser, DeserializationContext context) {
                    return Currency.getInstance(parser.getValueAsString());
                }
            });
            builder.addModule(module);
        };
    }
}
