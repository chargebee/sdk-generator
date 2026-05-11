# Zod Validator Generator — sdk-generator

## Overview

The validator generator is a subsystem inside `sdk-generator` that automatically produces
**Zod validation files** for `chargebee-node` (v3) and `chargebee-typescript-typings` (v3)
from the same OpenAPI specification used to generate the SDK itself.

The design is intentionally split into **two independent layers**:

1. **Validation IR** — a language-agnostic tree that describes *what* to validate
2. **Language AST + Printer** — describes *how* to express those constraints in TypeScript/Zod

This separation means the IR is built once from the spec and the Zod emitter is a pure
function from IR → TypeScript source. Adding a new validation target in future (Pydantic,
Zod for Go, etc.) only requires writing a new emitter — **the IR layer should not change.**

---

## Where it lives

```
sdk-generator/
└── src/main/java/com/chargebee/sdk/validator/
    ├── ValidatorEmitter.java          ← interface contract for all emitters
    ├── ValidatorZod.java              ← Language class (wired into Main.java as -l VALIDATOR_ZOD)
    │
    ├── ir/                            ← Layer 1: Validation IR (language-agnostic)
    │   ├── ValidationNode.java        ← sealed node type hierarchy
    │   ├── PropertyEntry.java         ← wraps a node with required/optional/default metadata
    │   ├── ValidationIRBuilder.java   ← OpenAPI Schema → IR tree (recursive)
    │   └── SharedSchemaRegistry.java  ← tracks $ref schemas shared across actions
    │
    ├── ast/js/                        ← Layer 2: JS/TS AST (used by all TS-based emitters)
    │   ├── JsNode.java                ← sealed AST node hierarchy
    │   ├── JsBuilder.java             ← fluent factory for building AST nodes
    │   └── TsPrinter.java             ← AST → formatted TypeScript source string
    │
    └── emitter/zod/                   ← Zod-specific emitter
        ├── ZodNamingStrategy.java     ← naming conventions for files and schema consts
        ├── ZodTypeMapper.java         ← IR node → Zod AST expression
        └── ZodTsEmitter.java          ← orchestrates emission, writes per-resource `.schema.ts` files
```

---

## Invoking the generator

```bash
cd ~/work/sdk-generator/ && \
  ./gradlew run --args="-l VALIDATOR_ZOD \
    -i $HOME/work/cb-openapi-generator/swagger-ui/sdk/external/chargebee_sdk_spec.json \
    -o ../chargebee-node/src/schema" && \
  cd -
```

The output directory is **always cleaned before writing** so stale files from removed
API actions are never left behind.

---

## Pipeline: how a spec becomes a validation file

```
chargebee_sdk_spec.json
        │
        │  parsed by swagger-parser into OpenAPI model
        ▼
  Spec / Resource / Action / Schema
        │
        │  ValidationIRBuilder.buildNode()
        ▼
  ValidationNode tree  (language-agnostic IR)
        │
        │  ZodTypeMapper.toZod() / buildZodObjectExpr()
        ▼
  JsNode AST  (in-memory TypeScript expression tree)
        │
        │  TsPrinter.print()
        ▼
  Formatted TypeScript source string
        │
        │  FileOp.WriteString.exec()
        ▼
  .schema.ts file on disk (one module per resource; see below)
```

---

## Layer 1 — Validation IR

### Node types

`ValidationNode` is a **sealed interface** — every possible schema shape is represented by
exactly one of its seven record subtypes:

| Node         | Fields                                                          | Represents                                          |
|--------------|-----------------------------------------------------------------|-----------------------------------------------------|
| `StringNode` | `maxLength`, `minLength`, `pattern`, `format`, `enumValues`     | `type: string` with any combination of constraints  |
| `NumberNode` | `integer`, `minimum`, `maximum`                                 | `type: integer` or `type: number`                   |
| `BooleanNode`| `defaultValue`                                                  | `type: boolean`                                     |
| `ArrayNode`  | `items` (recursive), `minItems`, `maxItems`                     | `type: array`                                       |
| `ObjectNode` | `properties` (Map of name → `PropertyEntry`), `allowUnknown`   | `type: object` with named fields                    |
| `MapNode`    | `valueSchema` (recursive)                                       | `additionalProperties` with typed values            |
| `RefNode`    | `targetName`                                                    | A `$ref` to a named shared schema                   |

Each `PropertyEntry` wraps a `ValidationNode` with field-level metadata:

```java
record PropertyEntry(
    ValidationNode node,
    boolean required,
    boolean optional,
    Object defaultValue,
    String description
)
```

### How the IR is built — `ValidationIRBuilder`

`ValidationIRBuilder.buildNode(schema, visiting)` is a recursive method that walks the
OpenAPI `Schema` tree and returns a `ValidationNode`. The `visiting` set tracks schemas
currently on the call stack to detect and break circular `$ref` chains.

**`$ref` resolution:**
When a `$ref` is encountered, the builder extracts the target schema name
(e.g. `#/components/schemas/PostalAddress` → `"PostalAddress"`), resolves the schema from
`openAPI.getComponents().getSchemas()`, recursively builds its IR node, and registers it in
`SharedSchemaRegistry`. A `RefNode` pointing at that name is returned. If a circular reference
is detected (the name is already in `visiting`), a `RefNode` is returned immediately without
recursing further.

**`x-cb-is-multi-value-attribute` handling:**
Chargebee's API uses flat query-string encoding for arrays — e.g.
`subscription_items[item_price_id][0]` and `subscription_items[unit_price][0]` are sibling
properties in the spec but logically form an array of objects. The builder detects this
extension on any property in an object schema and restructures the whole parent as
`ArrayNode { items: ObjectNode { ... } }`.

**Hidden properties:**
Properties marked `x-cb-hidden-from-client-sdk: true` are skipped entirely — they are
internal API fields that should not be exposed or validated client-side.

**Required detection:**
A property is considered required if it appears in the parent schema's `required` array
**or** if it carries the extension `x-cb-attribute-meta-comment: "required"`.

---

## Layer 2 — JavaScript/TypeScript AST

Rather than building up strings directly, the emitter constructs an in-memory **AST** first
and then prints it. This guarantees consistent formatting and makes the mapping code easy to
test independently.

### `JsNode` — sealed AST hierarchy

```
JsNode
 ├── Program             { body: List<JsNode> }
 ├── VariableDeclaration { name, init, kind: "const" }
 ├── RequireCall         { module, destructured? }   → import { z } from 'zod'
 ├── ExportAssignment    { name, value }             → export { schema }
 ├── MethodChain         { receiver, calls }         → z.string().max(50).optional()
 ├── ObjectExpression    { entries: List<(key, value)> }
 ├── ArrayExpression     { elements }
 ├── Identifier          { name }
 ├── Literal             { value }                   → 'hello', 42, true
 ├── MemberAccess        { object, property }        → z.string
 └── CallExpression      { callee, args }            → z.string()
```

### `JsBuilder` — fluent construction

`JsBuilder` provides static factory methods so the mapper code reads close to the output it
produces:

```java
// Produces: z.string().max(50).optional()
JsBuilder.chain(
    JsBuilder.callExpr(JsBuilder.member("z", "string")),
    List.of(
        JsBuilder.call("max", JsBuilder.lit(50)),
        JsBuilder.call("optional")
    )
)
```

### `TsPrinter` — AST → TypeScript source

`TsPrinter.print(node)` walks the `JsNode` tree recursively and serialises each node to its
TypeScript equivalent with 2-space indentation:

| `JsNode` type                                         | TypeScript emitted                                          |
|-------------------------------------------------------|-------------------------------------------------------------|
| `RequireCall("zod", ["z"])`                           | `import { z } from 'zod';`                                  |
| `RequireCall("./shared.schema.js", ["fooSchema"])` | `import { fooSchema } from './shared.schema.js';`    |
| `TypeInferExport("CreateFooBody", "CreateFooBodySchema")` | `export type CreateFooBody = z.infer<typeof CreateFooBodySchema>;` |
| `VariableDeclaration("s", expr)`                      | `const s = expr;`                                           |
| `ExportAssignment("s", id("s"))`                      | `export { s };`                                             |
| `MethodChain(base, [call("max", [lit(50)])])`         | `base.max(50)`                                              |
| `ObjectExpression([prop("a", expr)])`                 | `{\n  a: expr\n}`                                           |

The printer never produces a self-referential `export const x = x` — when an
`ExportAssignment`'s value is an `Identifier` with the same name, it emits `export { x }`
instead.

---

## Layer 3 — Zod Emitter

### `ZodNamingStrategy`

All naming decisions are centralised here so the output is predictable and consistent:

| Concept             | Formula                                      | Example                          |
|---------------------|----------------------------------------------|----------------------------------|
| Resource module file | `{snake_resource}.schema.ts`               | `customer.schema.ts`, `virtual_bank_account.schema.ts` |
| Shared $ref file     | `shared.schema.ts`                         | —                                                        |
| Body schema const   | `{PascalAction}{PascalResource}BodySchema` | `CreateCustomerBodySchema`                               |
| Inferred body type  | same as const with trailing `Schema` removed | `CreateCustomerBody` (= `z.infer<typeof CreateCustomerBodySchema>`) |
| Nested sub-schema   | `{PascalAction}{PascalResource}{PascalProp}Schema` | `CreateCustomerCardSchema`                        |
| Shared schema       | `{camelRefName}BlockSchema`                | `postalAddressBlockSchema`                              |

### `ZodTypeMapper` — IR → Zod expression

`ZodTypeMapper` is the heart of the Zod emitter. It converts each `ValidationNode` into a
`JsNode` expression. It holds two pieces of mutable state during a single action's emission:

- **`nestedDecls`** — a list of `VariableDeclaration`s for nested object schemas that need
  to be hoisted out of the main expression and declared as named `const`s above it.
- **`declaredNames`** — a `Set<String>` that prevents the same nested schema from being
  declared twice when multiple properties in the same action happen to reference the same
  sub-object name (e.g. multiple fields all having an `additional_information` child).

**Full IR → Zod mapping:**

| IR node                                  | Zod expression emitted               |
|------------------------------------------|--------------------------------------|
| `StringNode { enumValues: ["a","b"] }`   | `z.enum(['a', 'b'])`                 |
| `StringNode { format: "email" }`         | `z.string().email()`                 |
| `StringNode { format: "uri" }`           | `z.string().url()`                   |
| `StringNode { format: "date-time" }`     | `z.string().datetime()`              |
| `StringNode { max: 50, min: 1 }`         | `z.string().max(50).min(1)`          |
| `StringNode { pattern: "^[a-z]+$" }`     | `z.string().regex(RegExp('^[a-z]+$'))` |
| `NumberNode { integer: true, min: 1, max: 12 }` | `z.number().int().min(1).max(12)` |
| `NumberNode { integer: false }`          | `z.number()`                         |
| `BooleanNode { default: false }`         | `z.boolean().default(false)`         |
| `BooleanNode { default: null }`          | `z.boolean()`                        |
| `ArrayNode { items: StringNode }`        | `z.array(z.string())`                |
| `ArrayNode { items: ..., min: 1 }`       | `z.array(...).min(1)`                |
| `ObjectNode { allowUnknown: true }`      | `z.looseObject({ ... })`             |
| `ObjectNode { allowUnknown: false }`     | `z.object({ ... })`                  |
| `MapNode { valueSchema: StringNode }`    | `z.record(z.string(), z.string())`   |
| `RefNode { "PostalAddress" }`            | `postalAddressBlockSchema` (identifier) |

After computing the base expression, `applyOptional()` appends `.optional()` to every
property that is not explicitly required. For identifiers (references to named schemas),
`.optional()` is appended as a chain; for `MethodChain` and `CallExpression` nodes it is
added to the end of the existing chain.

**Important Zod v4 note:**
`.passthrough()` is deprecated in Zod v4. The generator uses `z.looseObject({...})` for
schemas that allow unknown keys (i.e. top-level body schemas and schemas marked with
`additionalProperties`). Plain `z.object({...})` is used for strictly-typed nested schemas.

### `ZodTsEmitter` — orchestration

`ZodTsEmitter` iterates over every `Resource` in the spec. For each resource it collects
every `Action` (in `sortOrder`) that has a validateable body or query schema. All such
actions are emitted into a **single flat module** `{snake_resource}.schema.ts`.

For each qualifying action:

1. **Resolve the raw body schema** from the OpenAPI path item's `requestBody` content
   (specifically the `application/x-www-form-urlencoded` media type), or build a synthetic
   object schema from GET query parameters.
2. **Build the IR** by calling `ValidationIRBuilder.buildNode()`. All `$ref` schemas
   encountered during this call are automatically registered in `SharedSchemaRegistry`.
3. **Force `allowUnknown: true`** on the top-level body schema — API responses may add
   new fields at any time, and client-side validation should not reject valid future params.
4. **Map IR → Zod AST** via `ZodTypeMapper`. Nested object schemas are extracted into named
   `const` declarations and collected in `nestedDecls`.
5. **Collect shared ref names** across all actions on that resource; emit one combined
   `import { … } from './shared.schema.js'` at the top when needed.
6. **Assemble each action block** as `JsNode`s in this order:
   - Section comment `// Resource.action`
   - Nested `const` declarations (hoisted sub-schemas)
   - Main body / query schema `const`
   - `export { …BodySchema }`
   - `export type …Body = z.infer<typeof …BodySchema>`
7. **Print to string** via `TsPrinter` and queue one `FileOp.WriteString` per resource.

After all resources are processed, two additional files are written:

- **`shared.schema.ts`** — contains all `$ref`-resolved schemas registered in
  `SharedSchemaRegistry`. Every `$ref` target becomes a shared schema regardless of how
  many actions reference it.
- **`index.ts`** — a barrel file that re-exports every resource module and everything from
  `shared.schema.ts`, so consumers can import any schema from a single entry point.

---

## Output file structure

```
src/schema/
├── shared.schema.ts                  ← all $ref schemas (postal address, payment intent, etc.)
├── index.ts                          ← export * from every module below
├── customer.schema.ts                ← create, update, list, … for Customer
├── addon.schema.ts
├── subscription.schema.ts
└── ... (one file per resource that has at least one validateable action)
```

### Example fragment — `customer.schema.ts`

Each resource module lists every action’s body/query schema in SDK `sortOrder`, then exports
matching `z.infer` types (e.g. `CreateCustomerBody`) for use at call sites.

```typescript
// Generated Zod schemas: Customer
// Actions: create, update, list, ...
// Do not edit manually – regenerate via sdk-generator

import { z } from 'zod';
import { addressBlockSchema } from './shared.schema.js';

// Customer.create

const CreateCustomerCardSchema = z.object({ ... });
const CreateCustomerBodySchema = z.looseObject({
  card: CreateCustomerCardSchema.optional(),
  ...
});
export { CreateCustomerBodySchema };
export type CreateCustomerBody = z.infer<typeof CreateCustomerBodySchema>;

// Customer.update

const UpdateCustomerBodySchema = z.looseObject({ ... });
export { UpdateCustomerBodySchema };
export type UpdateCustomerBody = z.infer<typeof UpdateCustomerBodySchema>;
```

### Older layout (removed)

Per-action files under `src/validation/{resource}/{action}.validation.ts` are **no longer**
emitted; regenerate into `src/schema` and update imports accordingly.

---

## Extensibility

The two-layer design makes adding a new validation target straightforward:

1. Create a new `emitter/` package (e.g. `emitter/pydantic/`)
2. Implement `ValidatorEmitter` — one method: `emit(spec, registry, outputDir)`
3. Write a `TypeMapper` that converts `ValidationNode` subtypes to the target language's AST
4. Add a `Language` class and a new `Lang` enum entry in `Main.java`

The Validation IR (`ValidationNode`, `ValidationIRBuilder`, `SharedSchemaRegistry`) is
**unchanged** — it describes what to validate, not how to express it.

---

## Design decisions

### Why AST instead of string templates?

Handlebars templates (used by all other language generators in this codebase) become
difficult to maintain for deeply nested, conditionally-structured validation code:

| Concern                                          | Handlebars templates              | AST-based                                        |
|--------------------------------------------------|-----------------------------------|--------------------------------------------------|
| Nested objects                                   | Recursive partials, hard to debug | Natural Java recursion                           |
| Method chains like `.max(50).min(1).optional()`  | `{{#if}}` spaghetti               | Programmatic list of `MethodCall` nodes          |
| Shared schema extraction                         | Manual include logic              | Registry-driven, fully automatic                 |
| New language support                             | Write templates from scratch      | Implement one new emitter, reuse IR              |
| Type safety                                      | String maps                       | Sealed types, exhaustive matching                |
| Unit testing                                     | Snapshot-only                     | Test individual IR→AST transforms in isolation   |

### Why two layers (IR + Language AST)?

Separating "what to validate" (IR) from "how to express it in code" (Language AST) means:

- The IR builder is written **once** and tested against the OpenAPI spec
- Each language emitter is a **pure function** from IR → source code, independently testable
- Validation rules stay **consistent across languages** — single source of truth in the spec

### Why inside `sdk-generator` (Java)?

- Reuses the existing OpenAPI parsing infrastructure (`Spec`, `Resource`, `Action`,
  `Attribute`) including all the Chargebee-specific extension handling (`x-cb-*`)
- Single CI pipeline for all SDK and validation generation
- The JS/TS AST nodes we build in Java are simple — we are generating code, not parsing it

### Why `z.looseObject()` instead of `z.object().passthrough()`?

Zod v4 deprecated `.passthrough()` — the type-correct replacement is `z.looseObject({...})`
which produces a `ZodObject` with `$loose` unknown-key behaviour. The generator uses
`z.looseObject` for any schema where `allowUnknown` is `true` (all top-level body schemas
and schemas with `additionalProperties`) and `z.object` for fully-typed nested schemas.
