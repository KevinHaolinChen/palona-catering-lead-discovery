package com.palona.cateringleads.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ClaimKind {
    FACT("fact"),
    INFERENCE("inference"),
    AI_GENERATED("ai_generated");

    private final String jsonValue;

    ClaimKind(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static ClaimKind fromJson(String value) {
        for (ClaimKind kind : values()) {
            if (kind.jsonValue.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown claim kind: " + value);
    }
}
