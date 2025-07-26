# Contributing to SDK Generator

Thank you for your interest in contributing to the Chargebee SDK Generator! This document provides guidelines and information for contributors.

## 🚀 Getting Started

### Prerequisites

- **Java 17 or higher**
- **Gradle 7.0 or higher**
- **Git**

### Setting Up the Development Environment

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/chargebee/sdk-generator.git
   cd sdk-generator
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run tests**
   ```bash
   ./gradlew test
   ```

4. **Set up pre-commit hooks**
   ```bash
   pre-commit install
   ```

## 🛠️ Development Workflow

### Code Style and Formatting

This project uses **Spotless** for code formatting. Before submitting any changes:

```bash
# Check formatting
./gradlew spotlessCheck

# Apply formatting
./gradlew spotlessApply
```

### Testing

- Write unit tests for new functionality
- Ensure all existing tests pass
- Aim for good test coverage

```bash
# Run all tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport
```

### Building and Running

```bash
# Build the project
./gradlew build

# Run the generator
./gradlew run --args="-i spec.json -l JAVA -o output/"
```

## 📝 Making Contributions

### Types of Contributions

We welcome various types of contributions:

- 🐛 **Bug fixes**
- ✨ **New features**
- 📚 **Documentation improvements**
- 🧪 **Test improvements**
- 🔧 **Code refactoring**
- 🌐 **New language support**

### Before You Start

1. **Check existing issues** - Look for existing issues or feature requests
2. **Create an issue** - For significant changes, create an issue to discuss the approach
3. **Fork the repository** - Create your own fork to work on

### Pull Request Process

1. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew spotlessCheck
   ```

4. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add support for new language template"
   ```

5. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Create a Pull Request**
   - Use the provided PR template
   - Provide a clear description of changes
   - Link related issues
   - Include testing instructions

### Commit Message Guidelines

We follow conventional commit format:

- `feat:` - New features
- `fix:` - Bug fixes
- `docs:` - Documentation changes
- `test:` - Test improvements
- `refactor:` - Code refactoring
- `chore:` - Build/tooling changes

Examples:
```
feat: add support for Go SDK generation
fix: resolve template rendering issue for PHP arrays
docs: update README with new language support
test: add unit tests for Java template generator
```

## 🏗️ Project Structure

```
sdk-generator/
├── src/
│   ├── main/
│   │   ├── java/           # Main application code
│   │   └── resources/      # Templates and resources
│   └── test/               # Test files
├── docs/                   # Documentation
├── build.gradle           # Build configuration
└── README.md              # Project overview
```

### Key Components

- **Templates**: Language-specific code generation templates (Handlebars)
- **Generators**: Language-specific generator classes
- **Models**: Data models for OpenAPI parsing
- **CLI**: Command-line interface implementation

## 🌐 Adding Support for New Languages

To add support for a new programming language:

1. **Create template files** in `src/main/resources/templates/{language}/`
2. **Implement generator class** extending the base generator
3. **Add language enum** to supported languages
4. **Create unit tests** for the new language
5. **Update documentation** including README.md

## 🐛 Reporting Issues

When reporting issues, please include:

- **Clear description** of the problem
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Environment details** (Java version, OS, etc.)
- **Sample OpenAPI spec** if relevant
- **Error logs** or stack traces

Use our issue templates when available.

## 📋 Code Review Guidelines

### For Contributors

- Keep PRs focused and atomic
- Write clear commit messages
- Add tests for new functionality
- Update documentation as needed
- Respond to review feedback promptly

### For Reviewers

- Be constructive and respectful
- Focus on code quality and maintainability
- Check for test coverage
- Verify documentation updates
- Test the changes locally when possible

## 🔒 Security

If you discover a security vulnerability, please follow our [Security Policy](SECURITY.md). Do not create public issues for security vulnerabilities.

## 📄 License

By contributing to this project, you agree that your contributions will be licensed under the same license as the project (see [LICENSE](LICENSE) file).

## ❓ Getting Help

- **Documentation**: Check the [README.md](README.md) and [docs/](docs/) folder
- **Issues**: Search existing issues or create a new one
- **Discussions**: Use GitHub Discussions for questions and ideas

## 🙏 Recognition

Contributors are recognized in our:
- GitHub contributor graphs
- Release notes for significant contributions
- Project documentation

Thank you for contributing to the Chargebee SDK Generator! 🎉
