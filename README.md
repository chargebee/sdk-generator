# SDK Generator

A powerful tool that automatically generates Software Development Kits (SDKs) for multiple programming languages from OpenAPI 3.0 specifications.

## 🚀 Features

- **Multi-language Support**: Generate SDKs for 8 programming languages
- **OpenAPI 3.0 Compatible**: Built on industry-standard OpenAPI specifications
- **Template-based Generation**: Customizable templates for each language
- **Type Safety**: Generates strongly-typed code with proper interfaces
- **CLI Interface**: Simple command-line tool for easy integration

## 📋 Supported Languages

- [Java](https://github.com/chargebee/chargebee-java)
- [Node.js](https://github.com/chargebee/chargebee-node)
- [Python](https://github.com/chargebee/chargebee-python)
- [PHP](https://github.com/chargebee/chargebee-php)
- [Ruby](https://github.com/chargebee/chargebee-ruby)
- [.NET](https://github.com/chargebee/chargebee-dotnet)
- [Go](https://github.com/chargebee/chargebee-go)

## 🛠️ Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher

### Download Latest OpenAPI Specification

To get the latest OpenAPI specification for Chargebee, you can download it from the [Chargebee OpenAPI repository](
https://github.com/chargebee/openapi/blob/main/spec/chargebee_sdk_spec.json)

### For Chargebee Client Library Generation

To generate SDKs for Chargebee's official client libraries, you'll need to clone the respective repositories first, as the generator updates existing code structure rather than creating everything from scratch:

```bash
# Clone all Chargebee client library repositories
git clone https://github.com/chargebee/chargebee-php.git
git clone https://github.com/chargebee/chargebee-node.git
git clone https://github.com/chargebee/chargebee-python.git
git clone https://github.com/chargebee/chargebee-ruby.git
git clone https://github.com/chargebee/chargebee-java.git
git clone https://github.com/chargebee/chargebee-dotnet.git
git clone https://github.com/chargebee/chargebee-go.git
```

**Note:** The SDK generator updates existing repository structures rather than creating complete projects from scratch. Make sure to clone the target repositories before running the generation commands.

## 📦 Installation

### From Source

```bash
git clone https://github.com/chargebee/sdk-generator.git
cd sdk-generator
./gradlew build
```

### Using Gradle

```bash
./gradlew run --args="-i spec.json -l JAVA -o ./output"
```

## 🚀 Quick Start

### Basic Usage

```bash
# Generate a Java SDK
./gradlew run --args="-i chargebee_sdk_spec.json -l JAVA -o ../chargebee-java/src/main/java/com/chargebee"

# Generate Nodejs typings
./gradlew run --args="-i chargebee_sdk_spec.json -l TYPESCRIPT_TYPINGS_V3 -o ../chargebee-node/types/"

# Generate Python SDK
./gradlew run --args="-i chargebee_sdk_spec.json -l PYTHON_V3 -o ../chargebee-python/chargebee"
```

### Command Line Options

| Option | Description | Required |
|--------|-------------|----------|
| `-i, --input` | Path to OpenAPI specification file | ✅ |
| `-l, --language` | Target language for SDK generation | ✅ |
| `-o, --output` | Output directory path | ✅ |

### Available Languages

#### Current/Latest Versions
```
JAVA                    - Java SDK
TYPESCRIPT_TYPINGS_V3  - TypeScript type definitions (v3)
NODE_V3                - Node.js SDK (v3)
PYTHON_V3              - Python SDK (v3)
PHP_V4                 - PHP SDK (v4)
RUBY                   - Ruby SDK
DOTNET                 - .NET SDK
GO                     - Go SDK
```

#### Legacy Versions
```
NODE                   - Node.js SDK (legacy)
PYTHON                 - Python SDK (legacy)
PHP                    - PHP SDK (legacy)
```

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/chargebee/
│   │   ├── openapi/           # OpenAPI parsing and processing
│   │   ├── sdk/               # Language-specific generators
│   │   └── Main.java          # CLI entry point
│   └── resources/
│       └── templates/         # Language-specific templates
│           ├── java/
│           ├── php/
│           ├── python/
│           └── ...
└── test/                      # Unit tests
```

## 🔧 Configuration

### Custom Templates

The generator uses Handlebars templates located in `src/main/resources/templates/`. You can customize the generated code by modifying these templates:

- `java/` - Java SDK templates
- `ts/` - TypeScript templates  
- `python/` - Python SDK templates
- `php/` - PHP SDK templates
- And more...

### OpenAPI Extensions

The generator supports custom OpenAPI extensions for enhanced functionality:

```yaml
# Example OpenAPI spec with custom extensions
components:
  schemas:
    User:
      type: object
      properties:
        id:
          type: string
          x-is-dependent-attribute: true
        email:
          type: string
          x-is-multi-attribute: false
```

## X-CB Extension Parameters

This SDK generator uses custom OpenAPI extension parameters (prefixed with `x-cb-`) to enhance code generation capabilities. These extensions provide additional metadata for:

- Operation behavior control (list operations, bulk operations, idempotency)
- Parameter filtering and ordering
- Resource relationships and sub-resources
- Special data type handling (money fields, custom fields)
- URL construction and routing

For a complete list and detailed descriptions of all supported extensions, see [X-CB Extensions Documentation](docs/X-CB-EXTENSIONS.md).

### Key Extensions

- `x-cb-operation-method-name`: Defines SDK method names
- `x-cb-operation-is-list`: Marks list operations for special handling
- `x-cb-is-filter-parameter`: Enables filter parameter generation
- `x-cb-resource-id`: Links operations to resource models
- `x-cb-is-money-column`: Special handling for monetary values

## 🧪 Testing

Run the test suite:

```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport
```

View coverage report at `build/jacocoHtml/index.html`

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Add tests for new functionality
5. Run tests: `./gradlew test`
6. Commit your changes: `git commit -m 'Add amazing feature'`
7. Push to the branch: `git push origin feature/amazing-feature`
8. Open a Pull Request

### Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Ensure all tests pass before submitting

## 📝 Examples

### Generating Multiple SDKs

#### Using Current/Latest Versions (Recommended)
```bash
# Generate SDKs using latest versions
./gradlew run --args="-i chargebee_sdk_spec.json -l JAVA -o ../chargebee-java/src/main/java/com/chargebee"
./gradlew run --args="-i chargebee_sdk_spec.json -l PHP_V4 -o ../chargebee-php/src"
./gradlew run --args="-i chargebee_sdk_spec.json -l PYTHON_V3 -o ../chargebee-python/chargebee"
./gradlew run --args="-i chargebee_sdk_spec.json -l TYPESCRIPT_TYPINGS_V3 -o ../chargebee-node/types/"
./gradlew run --args="-i chargebee_sdk_spec.json -l NODE_V3 -o ../chargebee-node/src/resources"
```

#### Using Legacy Versions
```bash
# Generate SDKs using legacy versions (not recommended for new projects)
./gradlew run --args="-i chargebee_sdk_spec.json -l PYTHON -o ./python-legacy-sdk"
./gradlew run --args="-i chargebee_sdk_spec.json -l NODE -o ./node-legacy-sdk"
./gradlew run --args="-i chargebee_sdk_spec.json -l PHP -o ./php-legacy-sdk"
```

### Batch Generation for Chargebee Client Libraries

Generate SDKs for all supported languages targeting Chargebee's official client library repositories:

**Prerequisites:** Clone all [Chargebee client repositories](https://github.com/chargebee) before running these commands.

#### Current/Latest Versions
```bash
# Generate current versions of SDKs
./gradlew run --args="-i chargebee_sdk_spec.json -l PHP_V4 -o ../chargebee-php/src" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l NODE_V3 -o ../chargebee-node/src/resources" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l PYTHON_V3 -o ../chargebee-python/chargebee" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l RUBY -o ../chargebee-ruby/lib/chargebee" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l TYPESCRIPT_TYPINGS_V3 -o ../chargebee-node/types/" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l JAVA -o ../chargebee-java/src/main/java/com/chargebee" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l DOTNET -o ../chargebee-dotnet/ChargeBee" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l GO -o ../chargebee-go" &&

echo "Current SDK versions generated successfully."
```

#### Legacy Versions (For Backward Compatibility)
```bash
# Generate legacy versions of SDKs
./gradlew run --args="-i chargebee_sdk_spec.json -l PHP -o ../chargebee-php/lib" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l NODE -o ../chargebee-node/lib/resources" &&

./gradlew run --args="-i chargebee_sdk_spec.json -l PYTHON -o ../chargebee-python/chargebee" &&

echo "Legacy SDK versions generated successfully."
```

### Note for Node.js (NODE_V3)

After generating the Node.js SDK (NODE_V3), run the following command in the SDK output directory to format the code:

```bash
npx prettier --write .
```

### Note for Python (PYTHON_V3)

After generating the Python SDK (PYTHON_V3), run the following command in the SDK output directory to format the code:

```bash
black .
```

### Note for GO

After generating the GO SDK, run the following command in the SDK output directory to format the code:

```bash
gofmt -w .
```

#### Repository Structure

The above commands assume the following repository structure with cloned Chargebee repositories:
```
parent-directory/
├── sdk-generator/                 # This repository
├── cb-openapi-generator/         # OpenAPI specifications
├── chargebee-php/                # https://github.com/chargebee/chargebee-php
├── chargebee-node/               # https://github.com/chargebee/chargebee-node
├── chargebee-python/             # https://github.com/chargebee/chargebee-python
├── chargebee-ruby/               # https://github.com/chargebee/chargebee-ruby
├── chargebee-java/               # https://github.com/chargebee/chargebee-java
├── chargebee-dotnet/             # https://github.com/chargebee/chargebee-dotnet
└── chargebee-go/                 # https://github.com/chargebee/chargebee-go
```

**Important:** The generator updates existing code within these repositories. Ensure you have the latest version of each repository before generating SDKs.


## Feedback

If you find any bugs or have any questions / feedback, open an issue in this repository or reach out to us on [dx@chargebee.com](mailto:dx@chargebee.com)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [OpenAPI Generator](https://openapi-generator.tech/) concepts
- Uses [Swagger Parser](https://github.com/swagger-api/swagger-parser) for OpenAPI parsing
- Template engine powered by [Handlebars.java](https://github.com/jknack/handlebars.java)

## 🔗 Related Projects

- [OpenAPI Specification](https://spec.openapis.org/oas/v3.0.3)
- [Swagger Editor](https://editor.swagger.io/)
- [OpenAPI Generator](https://openapi-generator.tech/)

---


**Made with ❤️ by the chargebee**
