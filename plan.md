# AST-Based Validator Generator — Plan

## Context

The existing `joi/` reference in `cb-openapi-generator` contains **hand-crafted** Joi validation
files derived from the OpenAPI spec. The goal is to automate this with an **AST-based generator**
inside `sdk-generator` that:

1. Generates Joi validators for `chargebee-node` today
2. Is architecturally extensible to other SDKs (Python/Pydantic, Go, etc.) tomorrow
3. Handles nested/recursive object schemas by building them on the fly

The existing `sdk-generator` uses **Handlebars templates** for all languages. Templates are
brittle for deeply nested, conditionally structured validation code. An AST approach gives us
programmatic composition, type-safe construction, and clean recursive traversal — exactly what
validators need.

---

## Architecture Overview

```
┌─────────────────┐
│  OpenAPI Spec    │   chargebee_sdk_spec.json
│  (JSON)          │
└────────┬────────┘
         │  parse (existing Swagger Parser)
         ▼
┌─────────────────┐
│  Spec / Resource │   existing openapi/ models
│  / Action /      │   (Resource, Action, Attribute, Enum)
│  Attribute       │
└────────┬────────┘
         │  Phase 1: Build Validation IR
         ▼
┌─────────────────┐
│  Validation IR   │   language-agnostic intermediate representation
│  (Schema Tree)   │   ValidationNode tree per action
└────────┬────────┘
         │  Phase 2: Emit via AST
         ▼
┌─────────────────┐
│  Language AST    │   e.g. Java AST nodes for Joi call expressions
│  Builder         │   (pluggable per target language)
└────────┬────────┘
         │  Phase 3: Print
         ▼
┌─────────────────┐
│  Generated Code  │   .validation.js / .validation.ts files
│  (on disk)       │
└─────────────────┘
```

The key insight: **two-layer AST separation**.

- **Layer 1 — Validation IR**: A language-agnostic tree describing *what* to validate
  (string with max 50, required enum of ["a","b"], nested object with fields X/Y/Z).
  This layer is built once from the OpenAPI spec and is reusable across all target languages.

- **Layer 2 — Language AST**: Language-specific code AST nodes (function calls, variable
  declarations, imports). Each target language has its own emitter that walks the Validation IR
  and produces code AST nodes. For Node/Joi this means `Joi.string().max(50).required()` as
  a chain of method-call AST nodes.

---

## Phase 1: Validation IR (Language-Agnostic Schema Tree)

### Node Types

```
ValidationNode (abstract base)
│
├── ObjectNode
│     properties: Map<String, PropertyEntry>   // name → (node, required, optional, default)
│     allowUnknown: boolean                    // maps to additionalProperties / .unknown(true)
│     ref: String?                             // if this is a $ref, the shared schema name
│
├── StringNode
│     maxLength: Integer?
│     minLength: Integer?
│     pattern: String?
│     format: String?              // "email", "uri", "date-time", etc.
│     enumValues: List<String>?    // .valid(...) constraints
│
├── NumberNode
│     integer: boolean             // integer vs float
│     minimum: Number?
│     maximum: Number?
│
├── BooleanNode
│     defaultValue: Boolean?
│
├── ArrayNode
│     items: ValidationNode        // recursive — the schema of each element
│     minItems: Integer?
│     maxItems: Integer?
│
├── MapNode                        // for additionalProperties with typed values
│     valueSchema: ValidationNode
│
└── RefNode
      targetName: String           // reference to a shared/named schema
```

Each `PropertyEntry` wraps a `ValidationNode` with metadata:
- `required: boolean` — from `x-cb-attribute-meta-comment` or OpenAPI `required` array
- `optional: boolean`
- `defaultValue: Object?`
- `description: String?`

### Building the IR

A recursive `ValidationIRBuilder` walks the OpenAPI `Attribute` / `Schema` tree:

```
buildNode(schema):
    if schema is $ref → resolve ref, check shared cache, return RefNode or recurse
    if schema.type == "object" → recurse into each property → ObjectNode
    if schema.type == "array"  → recurse into items → ArrayNode
    if schema.type == "string" → extract constraints → StringNode
    if schema.type == "integer"/"number" → extract constraints → NumberNode
    if schema.type == "boolean" → BooleanNode
```

**Shared schema detection**: When the same `$ref` target is used by multiple actions, the IR
builder registers it in a `SharedSchemaRegistry`. These become the equivalent of
`chargebee-shared.validation.js` — schemas extracted into a shared file and imported
where needed.

**Recursive/nested handling**: The builder naturally handles any nesting depth because it
recurses into `ObjectNode.properties` and `ArrayNode.items`. Circular references are broken
by emitting `RefNode` pointers and generating the schema as a separate named constant.

### Multi-Value Attribute Handling

Chargebee uses `x-cb-is-multi-value-attribute: true` for flat-encoded array params like
`subscription_items.item_price_id[]`. The IR builder detects this extension and restructures
these sibling properties into a proper `ArrayNode` containing an `ObjectNode`.

---

## Phase 2: Language AST Emitter (Pluggable)

### Interface

```java
public interface ValidatorEmitter {
    /** Emit all file operations for the given spec. */
    List<FileOp> emit(Spec spec, ValidationIR ir, Path outputDir);
}
```

Each emitter walks the `ValidationIR` (the collection of per-action schema trees + shared
schemas) and produces `FileOp.WriteString` operations containing generated source code.

### Node.js / Joi Emitter (First Target)

For the Joi emitter, we build a **JavaScript AST** using the existing sdk-generator's Java
codebase. We don't need a full JS parser — we define a minimal set of AST node types that
cover Joi code patterns:

```
CodeNode (abstract)
├── Program { body: List<Statement> }
├── VariableDeclaration { name, init: Expression, kind: "const" }
├── RequireCall { module: String, destructured: List<String>? }
├── ExportAssignment { name: String, value: Expression }
├── MethodChain { receiver: Expression, calls: List<MethodCall> }
│     e.g.  Joi.string().max(50).required()
│           receiver = MemberAccess("Joi", "string")
│           calls = [call("max", [50]), call("required", [])]
├── ObjectExpression { entries: List<(String, Expression)> }
├── ArrayExpression { elements: List<Expression> }
├── Identifier { name: String }
├── Literal { value: Object }  // string, number, boolean
└── MemberAccess { object: Expression, property: String }
```

**Mapping IR → Joi AST**:

| IR Node | Joi AST Output |
|---------|---------------|
| `StringNode{max:50}` | `MethodChain(Joi.string(), [max(50)])` |
| `StringNode{enum:["a","b"]}` | `MethodChain(Joi.string(), [valid("a","b")])` |
| `StringNode{format:"email"}` | `MethodChain(Joi.string(), [email({tlds:{allow:false}})])` |
| `NumberNode{integer:true, min:0}` | `MethodChain(Joi.number(), [integer(), min(0)])` |
| `BooleanNode{default:false}` | `MethodChain(Joi.boolean(), [default(false)])` |
| `ObjectNode{props, unknown:true}` | `MethodChain(Joi.object({...}), [unknown(true)])` |
| `ArrayNode{items: StringNode}` | `MethodChain(Joi.array(), [items(Joi.string())])` |
| `RefNode{target:"postalAddress"}` | `Identifier("postalAddressBlockSchema")` |

**Code Printer**: A simple `JoiCodePrinter` walks the AST and emits formatted JavaScript text
with proper indentation. No need for Prettier — the AST structure itself guarantees
consistent formatting.

### Future Emitters

| Target | Emitter | Output |
|--------|---------|--------|
| Node/Joi | `JoiEmitter` | `.validation.js` files |
| Node/Zod | `ZodEmitter` | `.schema.ts` files |
| Python/Pydantic | `PydanticEmitter` | `_validators.py` files |
| Go | `GoValidatorEmitter` | `_validate.go` files |
| Java | `JavaValidatorEmitter` | `Validator.java` files |

Adding a new language = implement `ValidatorEmitter` + define the IR→AST mapping.
The Validation IR stays the same.

---

## Phase 3: File Structure & Output

### Generated File Layout (Node/Joi)

```
chargebee-node/
└── src/
    └── validation/
        ├── shared.validation.js              # shared schemas (postal address, payment intent, etc.)
        ├── customer/
        │   ├── create.validation.js           # POST /customers
        │   ├── update.validation.js           # POST /customers/{id}
        │   └── list.validation.js             # GET /customers
        ├── subscription/
        │   ├── create_for_items.validation.js  # POST /subscriptions
        │   └── ...
        └── index.js                           # barrel export
```

Each file:
- Header comment with source spec reference
- `require("joi")` import
- Shared schema imports (if needed)
- Named `const` for each nested sub-schema
- Exported body schema as the main entry point
- `.unknown(true)` on top-level body schemas for forward compatibility

### Naming Conventions

| Concept | Naming Pattern |
|---------|---------------|
| File | `{action_name}.validation.js` |
| Body schema | `{action}{Resource}BodySchema` |
| Nested schema | `{action}{Resource}{SubObject}Schema` |
| Shared schema | `{subObjectName}BlockSchema` or `{subObjectName}BodySchema` |

---

## Implementation Plan (in `sdk-generator`)

### Step 1: Validation IR Core

**Package**: `com.chargebee.sdk.validator.ir`

- `ValidationNode.java` — sealed interface hierarchy for all node types
- `PropertyEntry.java` — wrapper with required/optional/default metadata
- `ValidationIRBuilder.java` — recursive builder from OpenAPI `Schema` → IR tree
- `SharedSchemaRegistry.java` — tracks shared `$ref` schemas across actions

**Deliverable**: Given a `Spec`, produce a full `ValidationIR` object containing per-action
schema trees and a set of shared schemas.

### Step 2: JavaScript Code AST

**Package**: `com.chargebee.sdk.validator.ast.js`

- `JsNode.java` — sealed interface hierarchy for JS AST nodes
- `JsBuilder.java` — fluent API for constructing JS AST nodes
  (e.g., `JsBuilder.require("joi")`, `JsBuilder.constDecl("schema", expr)`)
- `JsPrinter.java` — AST → formatted JavaScript source string

**Deliverable**: Ability to programmatically build and print arbitrary JavaScript code.

### Step 3: Joi Emitter

**Package**: `com.chargebee.sdk.validator.emitter.joi`

- `JoiEmitter.java` — implements `ValidatorEmitter`, walks IR → JS AST → files
- `JoiTypeMapper.java` — maps IR node types to Joi method chains
- `JoiNamingStrategy.java` — schema/file naming conventions

**Deliverable**: Generate `.validation.js` files matching the hand-crafted reference output.

### Step 4: Integration with CLI

- Add a new `Lang` enum entry (e.g., `VALIDATOR_JOI`) or a new CLI flag (`--validators`)
- Wire `JoiEmitter` into `Main.java`
- Add to `scripts/sdk-gen.sh`

**Deliverable**: `java -jar sdk-generator.jar -l validator-joi -i spec.json -o ./output`

### Step 5: Validation & Tests

- Golden-file tests: compare generated output against the hand-crafted `joi/` reference files
- Round-trip tests: generated validators should pass when fed valid API payloads
- Edge cases: circular refs, deeply nested objects, multi-value attributes, empty enums

---

## Design Decisions

### Why AST over Templates?

| Concern | Template (Handlebars) | AST-based |
|---------|----------------------|-----------|
| Nested objects | Recursive partials, hard to debug | Natural recursion via tree walking |
| Conditional chains | `{{#if}}` spaghetti for `.max().min().required()` | Programmatic chain building |
| Shared schema extraction | Manual include logic | Registry-driven, automatic |
| New language support | Write new templates from scratch | Implement new emitter, reuse IR |
| Type safety | Stringly-typed maps | Sealed types, compiler-checked |
| Testing | Snapshot-only | Unit test individual IR→AST transforms |

### Why Two-Layer (IR + Language AST)?

Separating the "what to validate" (IR) from the "how to express it in code" (Language AST)
means:

- The IR builder is written **once** and tested against the OpenAPI spec
- Each language emitter is a **pure function** from IR → code, easy to test in isolation
- Adding Python/Pydantic validators later requires zero changes to the IR layer
- Validation rules stay consistent across languages (single source of truth)

### Why Inside `sdk-generator` (Java)?

- Reuses the existing OpenAPI parsing infrastructure (`Spec`, `Resource`, `Attribute`)
- Reuses `x-cb-*` extension handling already implemented
- Consistent with how all other SDKs are generated
- The Joi/JS AST we build in Java is simple — we're not parsing JS, just generating it
- Single CI pipeline for all SDK generation

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Multi-value attribute encoding is complex | Dedicated IR pass to restructure flat params into nested arrays before emission |
| Circular `$ref` schemas | Track visited refs during IR building; emit `Joi.lazy()` or `RefNode` for cycles |
| OpenAPI spec has inconsistent extensions | Validate extensions during IR building; fail fast with clear errors |
| Generated code differs from hand-crafted reference | Golden-file test suite comparing output line-by-line |
| JS AST printer produces ugly code | Keep printer simple; add optional Prettier pass as post-processing |

---

## Open Questions

1. **Where do generated validators live?** Inside `chargebee-node/src/validation/` (co-located)
   or in a separate `chargebee-node-validators` package? 
2. **Runtime integration**: Should the SDK automatically call validators before HTTP requests,
   or are they opt-in?
3. **TypeScript vs JavaScript**: Generate `.validation.ts` with types for better DX, or
   `.validation.js` to avoid build-step dependency?
4. **Shared schema granularity**: Should every `$ref` become shared, or only refs used by 2+
   actions?


## Open questions answers: 

1. **Where do generated validators live?** Inside `chargebee-node/src/validation/` (co-located)
   or in a separate `chargebee-node-validators` package?
-> `chargebee-node/src/validation/`
2. **Runtime integration**: Should the SDK automatically call validators before HTTP requests,
   or are they opt-in?
-> `opt-in`
3. **TypeScript vs JavaScript**: Generate `.validation.ts` with types for better DX, or
   `.validation.js` to avoid build-step dependency?
-> types already there so no need. 
4. **Shared schema granularity**: Should every `$ref` become shared, or only refs used by 2+
   actions?
-> every refs will become shared. 
