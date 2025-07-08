# X-CB Extension Parameters

This document describes all the custom extension parameters (prefixed with `x-cb-`) used in the SDK generator for OpenAPI specification enhancement.

---

## Operation Extensions

| Parameter                          | Type     | Default | Required | Description                                                                 | Example                        |
|------------------------------------|----------|---------|----------|-----------------------------------------------------------------------------|--------------------------------|
| x-cb-operation-method-name         | string   |         | Yes      | Defines the method name for the generated SDK operation.                    | "create", "list", "retrieve"  |
| x-cb-operation-is-list             | boolean  | false   |          | Indicates whether the operation returns a list of resources.               |                                |
| x-cb-operation-is-bulk             | boolean  | false   |          | Marks an operation as a bulk operation.                                     |                                |
| x-cb-operation-is-batch            | boolean  | false   |          | Indicates that the operation is a batch operation.                         |                                |
| x-cb-operation-is-idempotent       | boolean  | false   |          | Marks an operation as idempotent (safe to retry).                          |                                |
| x-cb-operation-sub-domain-name     | string   |         |          | Specifies the sub-domain name for the operation.                           |                                |
| x-cb-batch-operation-path-id       | string   |         |          | Path identifier for batch operations.                                       |                                |
| x-cb-is-operation-needs-json-input | boolean  | false   |          | Indicates that the operation requires JSON input format.                    |                                |
| x-cb-is-operation-needs-input-object | boolean | false   |          | Indicates that the operation requires an input object.                     |                                |
| x-cb-is-custom-fields-supported    | boolean  | false   |          | Indicates if the operation supports custom fields.                          |                                |
| x-cb-sort-order                    | integer  | -1      |          | Defines the display order of operations in generated documentation and SDK. |                                |

---

## Parameter Extensions

| Parameter               | Type     | Default | Required | Description                                                                 | Example                        |
|-------------------------|----------|---------|----------|-----------------------------------------------------------------------------|--------------------------------|
| x-cb-is-filter-parameter| boolean  | false   |          | Marks a parameter as a filter parameter for list operations.                |                                |
| x-cb-is-sub-resource    | boolean  | false   |          | Indicates that a parameter represents a sub-resource.                       |                                |
| x-cb-is-pagination-parameter | boolean | false   |          | Marks a parameter as a pagination parameter.                                |                                |
| x-cb-parameter-blank-option | string |         |          | Specifies blank option handling for the parameter.                          |                                |
| x-cb-sdk-filter-name    | string   |         |          | Custom filter name for SDK generation.                                      |                                |
| x-cb-sort-order         | integer  | -1      |          | Defines the order of parameters in method signatures.                       |                                |

---

## Schema Extensions

| Parameter                   | Type     | Default | Required | Description                                                                 | Example                        |
|-----------------------------|----------|---------|----------|-----------------------------------------------------------------------------|--------------------------------|
| x-cb-is-money-column        | boolean  | false   |          | Indicates that a schema property represents a monetary value.              |                                |
| x-cb-is-long-money-column   | boolean  | false   |          | Indicates that a schema property represents a long monetary value.         |                                |
| x-cb-is-global-enum         | boolean  | false   |          | Marks a schema or property as a global enum, shared across models/resources.|                                |
| x-cb-global-enum-reference  | string   |         |          | Reference name of a global enum to use for a schema/property.              |                                |
| x-cb-is-external-enum       | boolean  | false   |          | Marks a schema/property as referencing an external enum definition.        |                                |
| x-cb-sdk-enum-api-name      | string   |         |          | Custom API name for enum in SDK generation.                                 |                                |
| x-cb-deprecated-enum-values | string   |         |          | Comma-separated list of deprecated enum values.                             |                                |
| x-cb-meta-model-name        | string   |         |          | Meta-model name for a schema/property, used for internal mapping/codegen.  |                                |
| x-cb-attribute-meta-comment | string   |         |          | Meta comment for the attribute/schema property.                             |                                |
| x-cb-attribute-pcv          | string   |         |          | Product catalog version for the attribute.                                  |                                |
| x-cb-is-api-column          | boolean  | false   |          | Indicates that the schema property is an API column.                       |                                |
| x-cb-is-foreign-column      | boolean  | false   |          | Indicates that the schema property is a foreign key column.                |                                |
| x-cb-is-multi-value-attribute | boolean | false   |          | Indicates that the attribute supports multiple values.                     |                                |
| x-cb-is-dependent-attribute | boolean  | false   |          | Indicates that the attribute depends on other attributes.                  |                                |
| x-cb-is-composite-array-request-body | boolean | false |          | Indicates that the request body is a composite array.                      |                                |

---

## Resource Extensions

| Parameter          | Type     | Default | Required | Description                                                                 | Example                        |
|--------------------|----------|---------|----------|-----------------------------------------------------------------------------|--------------------------------|
| x-cb-resource-id   | string   |         | Yes      | Specifies the resource identifier for the operation.                        |                                |
| x-cb-resource-path-name | string |         |          | Custom path name for the resource.                                          |                                |
| x-cb-is-global-resource-reference | boolean | false |          | Indicates that this is a global resource reference.                        |                                |
| x-cb-is-third-party-resource | boolean | false |          | Indicates that this is a third-party resource.                             |                                |
| x-cb-is-dependent-resource | boolean | false |          | Indicates that the resource depends on other resources.                    |                                |
| x-cb-sub-resource-name | string |         |          | Name of the sub-resource.                                                   |                                |
| x-cb-sub-resource-parent-name | string |         |          | Name of the parent resource for sub-resources.                             |                                |

---

## API Version and Control Extensions

| Parameter               | Type     | Default | Required | Description                                                                 | Example                        |
|-------------------------|----------|---------|----------|-----------------------------------------------------------------------------|--------------------------------|
| x-cb-api-version        | string   |         |          | Specifies the API version for the operation/resource.                       |                                |
| x-cb-product-catalog-version | string |         |          | Specifies the product catalog version.                                      |                                |
| x-cb-hidden-from-client-sdk | boolean | false |          | Hides the operation/resource from client SDK generation.                    |                                |
| x-cb-internal           | boolean  | false   |          | Marks the operation/resource as internal use only.                         |                                |
| x-cb-module             | string   |         |          | Specifies the module name for organization.                                 |                                |
| x-cb-is-eap             | boolean  | false   |          | Indicates Early Access Program feature.                                     |                                |
| x-cb-is-gen-separate    | boolean  | false   |          | Indicates that the resource should be generated separately.                 |                                |
| x-cb-is-presence-operator-supported | boolean | false |          | Indicates support for presence operators in filters.                       |                                |

---

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

### Global Enum Schema

```yaml
status:
  type: string
  enum: ["active", "inactive", "pending"]
  extensions:
    x-cb-is-global-enum: true
    x-cb-sdk-enum-api-name: "CustomerStatus"
```

---

## Notes

- Boolean extensions default to `false` when not specified
- Integer extensions default to `-1` when not specified
- String extensions have no default value and must be explicitly set if required
- Extensions are processed during SDK generation and affect the output structure
- Some extensions are language-specific and may not apply to all SDK targets
- Extensions prefixed with `x-cb-` are custom extensions specific to the Chargebee SDK generator
