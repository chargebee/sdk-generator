# X-CB Extension Parameters

This document describes all the custom extension parameters (prefixed with `x-cb-`) used in the SDK generator for OpenAPI specification enhancement.

## Operation Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-operation-method-name       | string    |         | Yes      | Defines the method name for the generated SDK operation.                    | "create", "list", "retrieve" |
| x-cb-operation-is-list           | boolean   | false   |          | Indicates whether the operation returns a list of resources.                 |                                 |
| x-cb-sort-order                  | integer   | -1      |          | Defines the display order of operations in generated documentation and SDK.  |                                 |
| x-cb-operation-is-bulk           | boolean   | false   |          | Marks an operation as a bulk operation.                                     |                                 |
| x-cb-is-operation-needs-json-input | boolean | false   |          | Indicates that the operation requires JSON input format.                     |                                 |
| x-cb-is-external-enum            | boolean   | false   |          | Marks a schema or property as referencing an external enum definition, rather than generating a new enum in the SDK. |                                 |
| x-cb-is-operation-idempotent     | boolean   | false   |          | Marks an operation as idempotent.                                          |                                 |
| x-cb-is-custom-fields-supported   | boolean   | false   |          | Indicates if the operation supports custom fields.                          |                                 |

## Parameter Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-is-filter-parameter         | boolean   | false   |          | Marks a parameter as a filter parameter for list operations.                |                                 |
| x-cb-is-sub-resource             | boolean   | false   |          | Indicates that a parameter represents a sub-resource.                       |                                 |
| x-cb-sort-order                  | integer   | -1      |          | Defines the order of parameters in method signatures.                       |                                 |

## Schema Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-is-money-column             | boolean   | false   |          | Indicates that a schema property represents a monetary value.              |                                 |
| x-cb-is-global-enum              | boolean   | false   |          | Marks a schema or property as a global enum, meaning it is shared across multiple models or resources. |                                 |
| x-cb-global-enum-reference       | string    |         |          | Specifies the reference name of a global enum to use for a schema or property. |                                 |
| x-cb-meta-model-name             | string    |         |          | Provides a meta-model name for a schema or property, typically used for internal mapping or code generation purposes. |                                 |

## Resource Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-resource-id                 | string    |         | Yes      | Specifies the resource identifier for the operation.                        |                                 |

## URL Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-url-prefix                  | string    |         |          | Defines the URL prefix for the operation endpoint.                          |                                 |
| x-cb-url-suffix                  | string    |         |          | Defines the URL suffix for the operation endpoint.                          |                                 |
| x-cb-subdomain                   | string    |         |          | Specifies a subdomain for the operation.                                   |                                 |

## Batch Operation Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-batch-id                   | string    |         |          | Identifier for batch operations.                                           |                                 |
| x-cb-model-name                 | string    |         |          | Specifies the model name for the operation.                                |                                 |

## Generation Control Extensions

| Parameter                       | Type      | Default | Required | Description                                                                 | Example                        |
|----------------------------------|-----------|---------|----------|-----------------------------------------------------------------------------|---------------------------------|
| x-cb-param-blank-option         | string    |         |          | Controls how blank/empty parameters are handled.                           | "as_empty", etc.              |

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
