package com.chargebee.sdk.test_data;

import io.swagger.v3.oas.models.Operation;

public record OperationWithPath(String path, Operation operation) {}
