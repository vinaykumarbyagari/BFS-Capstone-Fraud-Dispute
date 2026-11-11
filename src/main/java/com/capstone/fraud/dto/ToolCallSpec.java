package com.capstone.fraud.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ToolCallSpec {
    private String tool;
    private Map<String, Object> arguments = new LinkedHashMap<>();

    public ToolCallSpec() {}

    public ToolCallSpec(String tool, Map<String, Object> arguments) {
        this.tool = tool;
        this.arguments = arguments;
    }
}
