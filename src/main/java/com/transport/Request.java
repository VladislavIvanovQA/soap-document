package com.transport;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class Request {
    private String externalId;
    private int expire;
    private String request;
    private String responseQueue;
    private JsonNode payload;
}
