package com.flodiback.domain.meeting.analysis.dto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

class UnresolvedItemsDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            return parser.getValueAsString();
        }
        if (token == JsonToken.START_ARRAY) {
            return joinArray(parser.readValueAsTree());
        }
        return (String) context.handleUnexpectedToken(String.class, parser);
    }

    private String joinArray(JsonNode node) {
        List<String> items = new ArrayList<>();
        node.forEach(item -> {
            if (item == null || item.isNull()) {
                return;
            }
            String value = item.asText(null);
            if (value == null) {
                return;
            }
            String stripped = value.strip();
            if (!stripped.isBlank()) {
                items.add(stripped);
            }
        });
        return items.isEmpty() ? null : String.join("\n", items);
    }
}
