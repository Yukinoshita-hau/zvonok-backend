package com.zvonok.utils;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.zvonok.exception.InvalidBooleanFormatException;

public class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {
    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken().isBoolean()) {
            return p.getBooleanValue();
        }
        throw new InvalidBooleanFormatException("Field must be boolean");
    }
}
