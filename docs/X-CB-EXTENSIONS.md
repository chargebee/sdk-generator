# X-CB Extension Parameters

This document describes all the custom extension parameters (prefixed with `x-cb-`) used in the SDK generator for OpenAPI specification enhancement.

## Operation Extensions

### x-cb-operation-method-name
**Description**: Defines the method name for the generated SDK operation.
**Type**: `string`
**Required**: Yes
**Usage**: Used to generate consistent method names across different language SDKs.
**Example**: `"create"`, `"list"`, `"retrieve"`

### x-cb-operation-is-list
**Description**: Indicates whether the operation returns a list of resources.
**Type**: `boolean`
**Default**: `false`
**Usage**: Controls the return type generation and response handling for list operations.

### x-cb-sort-order
**Description**: Defines the display order of operations in generated documentation and SDK.
**Type**: `integer`
**Default**: `-1`
**Usage**: Operations are sorted by this value in ascending order.

### x-cb-operation-is-bulk
**Description**: Marks an operation as a bulk operation.
**Type**: `boolean`
**Default**: `false`
**Usage**: Used for special handling of bulk operations in SDK generation.

### x-cb-is-operation-needs-json-input
**Description**: Indicates that the operation requires JSON input format.
**Type**: `boolean`
**Default**: `false`
**Usage**: Controls request serialization and content-type headers.

### x-cb-is-operation-idempotent
**Description**: Marks an operation as idempotent.
**Type**: `boolean`
**Default**: `false`
**Usage**: Used to set idempotency headers and retry logic.

### x-cb-is-custom-fields-supported
**Description**: Indicates if the operation supports custom fields.
**Type**: `boolean`
**Default**: `false`
**Usage**: Enables additional properties handling in request/response models.

## Parameter Extensions

### x-cb-is-filter-parameter
**Description**: Marks a parameter as a filter parameter for list operations.
**Type**: `boolean`
**Default**: `false`
**Usage**: Generates appropriate filter method signatures and documentation.

### x-cb-is-sub-resource
**Description**: Indicates that a parameter represents a sub-resource.
**Type**: `boolean`
**Default**: `false`
**Usage**: Used for nested resource handling and parameter grouping.

### x-cb-sort-order
**Description**: Defines the order of parameters in method signatures.
**Type**: `integer`
**Default**: `-1`
**Usage**: Parameters are ordered by this value in generated method signatures.

## Schema Extensions

### x-cb-is-money-column
**Description**: Indicates that a schema property represents a monetary value.
**Type**: `boolean`
**Default**: `false`
**Usage**: Used for special formatting and type generation for money fields.

## Resource Extensions

### x-cb-resource-id
**Description**: Specifies the resource identifier for the operation.
**Type**: `string`
**Required**: Yes for resource operations
**Usage**: Links operations to their corresponding resource models.

## URL Extensions

### x-cb-url-prefix
**Description**: Defines the URL prefix for the operation endpoint.
**Type**: `string`
**Usage**: Used to construct the full API endpoint URL.

### x-cb-url-suffix
**Description**: Defines the URL suffix for the operation endpoint.
**Type**: `string`
**Usage**: Appended to the base URL for specific operation variants.

### x-cb-subdomain
**Description**: Specifies a subdomain for the operation.
**Type**: `string`
**Usage**: Used for operations that require different subdomains.

## Batch Operation Extensions

### x-cb-batch-id
**Description**: Identifier for batch operations.
**Type**: `string`
**Usage**: Groups related operations for batch processing.

### x-cb-model-name
**Description**: Specifies the model name for the operation.
**Type**: `string`
**Usage**: Used when the model name differs from the resource name.

## Generation Control Extensions

### x-cb-param-blank-option
**Description**: Controls how blank/empty parameters are handled.
**Type**: `string`
**Values**: `"as_empty"`, etc.
**Usage**: Special handling for optional parameters in different generation modes.

## Examples

### Operation with List Support
```yaml
get:
  operationId: list_customers
  extensions:
    x-cb-operation-method-name: "list"
    x-cb-operation-is-list: true
    x-cb-sort-order: 1
    x-cb-resource-id: "customer"
```

### Parameter with Filter Support
```yaml
parameters:
  - name: status
    in: query
    schema:
      type: string
    extensions:
      x-cb-is-filter-parameter: true
      x-cb-sort-order: 1
```

### Money Field Schema
```yaml
amount:
  type: integer
  extensions:
    x-cb-is-money-column: true
```

## Notes

- Boolean extensions default to `false` when not specified
- Integer extensions default to `-1` when not specified
- Extensions are processed during SDK generation and affect the output structure
- Some extensions are language-specific and may not apply to all SDK targets
