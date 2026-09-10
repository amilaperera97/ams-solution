# CLAUDE.md

## Purpose

This file defines the engineering standards and development rules for this Java Spring Boot project.

Claude must follow these guidelines whenever creating, modifying, refactoring, reviewing, or testing code.

The primary goals are:

* Production-quality Java code
* Clean and maintainable architecture
* Simple and readable implementations
* Low unnecessary boilerplate
* Strong type safety
* Appropriate use of modern Java features
* Effective Spring Boot practices
* High-quality automated tests
* Meaningful logging and observability
* Secure-by-default implementation
* Clear error handling
* Easy long-term maintenance
* Small, focused classes and methods
* Avoiding over-engineering
* Avoiding duplication
* Avoiding unnecessary abstractions

When there is a conflict between these guidelines and an explicit project requirement, the explicit project requirement takes precedence.

---

# 1. General Engineering Principles

Always prioritize:

1. Correctness
2. Security
3. Maintainability
4. Readability
5. Testability
6. Performance
7. Simplicity

Do not optimize prematurely.

Prefer simple code that is easy for another engineer to understand and maintain.

### Follow these principles

* SOLID principles
* DRY — Don't Repeat Yourself
* KISS — Keep It Simple
* YAGNI — You Aren't Gonna Need It
* Separation of concerns
* Single responsibility
* Fail fast where appropriate
* Explicit dependencies
* Immutable data where practical
* Composition over inheritance
* Prefer interfaces at meaningful architectural boundaries
* Avoid unnecessary design patterns
* Avoid unnecessary abstraction layers

Do not introduce an abstraction simply because it is theoretically reusable.

An abstraction should exist because there is a real architectural or maintenance benefit.

---

# 2. Java Version and Modern Java

Use the Java version configured by the project.

Prefer modern Java language features when they improve readability and reduce boilerplate.

Use appropriate features such as:

* Records
* Sealed classes/interfaces where appropriate
* Pattern matching
* Switch expressions
* `var` where it improves readability
* `Optional`
* Streams where appropriate
* Text blocks where appropriate
* `List.of()`
* `Set.of()`
* `Map.of()`
* `Objects.requireNonNull()`
* `java.time` APIs

Do not use modern language features merely for novelty.

The resulting code must remain understandable to the project's developers.

---

# 3. Records — Prefer Records Over Boilerplate DTOs

Use Java `record` classes wherever the object represents immutable data.

Prefer:

```java
public record CertificateResponse(
        String id,
        String domain,
        String status
) {
}
```

instead of creating traditional DTO classes containing:

* private fields
* getters
* setters
* constructors
* `equals()`
* `hashCode()`
* `toString()`

Do not create boilerplate POJOs when a record is appropriate.

### Good candidates for records

Use records for:

* REST request DTOs
* REST response DTOs
* API payloads
* immutable configuration objects
* value objects
* query results
* event payloads
* simple service result objects
* immutable command objects

### Do not use records blindly

Do not use records when the class requires:

* mutable state
* complex lifecycle management
* framework-specific proxying requirements
* entity semantics where the framework requires a conventional class
* extensive mutable behavior

For JPA entities, do not automatically convert entities to records.

Prefer records for the boundary/data-transfer layer and normal classes/entities where required by persistence technology.

### Record validation

Records can contain validation annotations:

```java
public record CreateCertificateRequest(
        @NotBlank String domain,
        @NotBlank String environment
) {
}
```

Use records to make immutability and intent explicit.

---

# 4. Immutability

Prefer immutable objects.

Prefer:

```java
public record Certificate(String id, String domain) {
}
```

over mutable DTOs where appropriate.

Avoid unnecessary setters.

Do not expose mutable internal collections.

Prefer:

```java
return List.copyOf(certificates);
```

when returning collections that should not be modified.

Use `final` for variables and fields where it improves clarity.

Prefer constructor injection and immutable dependencies.

---

# 5. Spring Boot Architecture

Use a clear separation of responsibilities.

A typical structure should be:

```text
controller/
service/
repository/
client/
config/
exception/
model/
dto/
mapper/
util/
```

However, do not create packages or layers that provide no real value.

For larger applications, prefer feature/domain-oriented organization where appropriate:

```text
certificate/
    controller/
    service/
    repository/
    dto/
    mapper/

renewal/
    controller/
    service/
    repository/
```

The architecture should reflect the actual complexity of the application.

---

# 6. Controller Layer

Controllers must be thin.

Controllers should:

* Accept HTTP requests
* Validate input
* Delegate business operations
* Return appropriate responses
* Handle HTTP-specific concerns

Controllers must NOT contain significant business logic.

Avoid:

```java
@PostMapping
public ResponseEntity<?> create(...) {
    // 100 lines of business logic
}
```

Prefer:

```java
@PostMapping
public ResponseEntity<CertificateResponse> create(
        @Valid @RequestBody CreateCertificateRequest request) {

    return ResponseEntity.ok(certificateService.create(request));
}
```

### Controller rules

* Use appropriate HTTP methods
* Use meaningful endpoint names
* Use appropriate status codes
* Validate request DTOs
* Avoid exposing internal domain/entity objects directly
* Return dedicated response DTOs where appropriate
* Keep methods short
* Do not catch generic exceptions in controllers

---

# 7. REST API Best Practices

Use standard HTTP semantics.

Examples:

```text
GET     /api/certificates
GET     /api/certificates/{id}
POST    /api/certificates
PUT     /api/certificates/{id}
PATCH   /api/certificates/{id}
DELETE  /api/certificates/{id}
```

Use appropriate status codes:

* `200 OK`
* `201 Created`
* `202 Accepted`
* `204 No Content`
* `400 Bad Request`
* `401 Unauthorized`
* `403 Forbidden`
* `404 Not Found`
* `409 Conflict`
* `422 Unprocessable Entity` where appropriate
* `429 Too Many Requests`
* `500 Internal Server Error`
* `502 Bad Gateway`
* `503 Service Unavailable`

Do not return `200 OK` for every situation.

---

# 8. Service Layer

Business logic belongs in services/domain components rather than controllers.

Services should:

* Coordinate business operations
* Apply business rules
* Validate business conditions
* Call repositories and external clients
* Handle transactional boundaries where appropriate

Avoid extremely large service classes.

If a service becomes difficult to understand, identify separate responsibilities and extract them.

Do not create a service method that simply forwards a repository call unless the service boundary has an architectural purpose.

---

# 9. Repository Layer

Repositories should focus on persistence.

Do not put business logic inside repositories.

Prefer Spring Data repositories where appropriate.

Use explicit queries when necessary.

Avoid loading entire datasets when only a subset is required.

Prefer projections or targeted queries where appropriate.

Consider pagination for potentially large datasets.

Do not introduce unnecessary custom repository implementations.

---

# 10. JPA / Hibernate Best Practices

When using JPA:

* Avoid exposing entities directly through REST APIs
* Use DTOs/records at API boundaries
* Understand lazy vs eager loading
* Prefer lazy relationships by default where appropriate
* Avoid accidental N+1 queries
* Use transactions deliberately
* Keep transaction boundaries clear
* Avoid unnecessary database calls
* Avoid loading large collections unnecessarily

Be careful with:

```java
@OneToMany
@ManyToMany
@ManyToOne
@OneToOne
```

Do not blindly use `FetchType.EAGER`.

Avoid bidirectional relationships unless genuinely required.

Be careful with `equals()` and `hashCode()` for JPA entities.

Do not use Lombok-generated equality blindly for entities.

---

# 11. Dependency Injection

Use constructor injection.

Prefer:

```java
@Service
public class CertificateService {

    private final CertificateRepository repository;

    public CertificateService(CertificateRepository repository) {
        this.repository = repository;
    }
}
```

or the project's approved constructor-injection mechanism.

Avoid field injection:

```java
@Autowired
private CertificateRepository repository;
```

Constructor injection provides:

* Explicit dependencies
* Easier testing
* Immutable dependencies
* Better design
* Easier reasoning

Do not use service locators or static dependency access.

---

# 12. Configuration

Use Spring configuration mechanisms appropriately.

Prefer strongly typed configuration:

```java
@ConfigurationProperties(prefix = "certificate")
public record CertificateProperties(
        Duration renewalWindow,
        int retryAttempts
) {
}
```

Avoid scattering configuration lookups throughout the application.

Avoid:

```java
environment.getProperty("some.property")
```

throughout business logic.

Configuration should be centralized and type-safe.

Do not hard-code:

* URLs
* credentials
* timeouts
* retry counts
* environment-specific values
* feature flags
* AWS resource identifiers

Use configuration/environment variables/secrets management as appropriate.

---

# 13. Exception Handling

Use meaningful, domain-specific exceptions where appropriate.

Example:

```java
public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(String certificateId) {
        super("Certificate not found: " + certificateId);
    }
}
```

Do not use generic exceptions as business signals:

```java
throw new Exception("Something went wrong");
```

Avoid excessive custom exception classes.

Only create a custom exception when it communicates meaningful domain/application behavior.

---

# 14. Global Exception Handling

Use `@RestControllerAdvice` for consistent API error handling.

Example:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CertificateNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(
            CertificateNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError("CERTIFICATE_NOT_FOUND", exception.getMessage()));
    }
}
```

Use a consistent error response.

Prefer a record:

```java
public record ApiError(
        String code,
        String message
) {
}
```

Never expose:

* stack traces
* internal implementation details
* database errors
* credentials
* secrets
* internal infrastructure details

to API consumers.

---

# 15. Logging

Logging is mandatory for important application events.

Use the project's standard logging framework, normally SLF4J with Logback through Spring Boot.

Prefer:

```java
private static final Logger log =
        LoggerFactory.getLogger(CertificateService.class);
```

Use structured and meaningful messages.

### Good logging

```java
log.info("Starting certificate renewal for certificateId={}", certificateId);
```

```java
log.warn("Certificate renewal skipped because certificate is already valid, certificateId={}",
        certificateId);
```

```java
log.error("Certificate renewal failed, certificateId={}", certificateId, exception);
```

### Do not log sensitive information

Never log:

* passwords
* access tokens
* refresh tokens
* API keys
* private keys
* secrets
* session identifiers
* authentication headers
* full credentials
* sensitive personal information

Be especially careful with:

```java
log.info("Request: {}", request);
```

because `toString()` may expose sensitive fields.

### Log levels

Use levels appropriately.

#### DEBUG

Detailed information useful during development/troubleshooting.

```java
log.debug("Checking certificate configuration for environment={}", environment);
```

#### INFO

Important application lifecycle/business events.

```java
log.info("Certificate renewal completed, certificateId={}", certificateId);
```

#### WARN

Unexpected but recoverable situations.

```java
log.warn("Certificate renewal retry scheduled, certificateId={}, attempt={}",
        certificateId, attempt);
```

#### ERROR

Failures requiring attention.

```java
log.error("Certificate renewal failed, certificateId={}", certificateId, exception);
```

Do not log normal successful operations at ERROR.

Do not use INFO for extremely noisy loop-level logging.

---

# 16. Logging Exceptions Correctly

When logging an exception, preserve the exception object:

```java
log.error("Failed to renew certificate, certificateId={}",
        certificateId,
        exception);
```

Do NOT do:

```java
log.error("Failed: {}", exception.getMessage());
```

when the stack trace is needed for troubleshooting.

Do not log the same exception repeatedly at multiple layers unless each log provides meaningful additional context.

Prefer logging an error at the boundary where it is handled.

---

# 17. Correlation IDs and Observability

For distributed systems, support correlation/request IDs where appropriate.

Logs should make it possible to trace an operation across:

```text
Request
  -> Controller
  -> Service
  -> External Client
  -> AWS/service
  -> Database
```

Use Spring/Micrometer/observability mechanisms where available rather than manually reinventing tracing.

Do not introduce custom correlation mechanisms if the platform already provides one.

---

# 18. Logging and Business Identifiers

Where useful, include non-sensitive identifiers:

```java
log.info(
    "Certificate renewal started, certificateId={}, environment={}",
    certificateId,
    environment
);
```

Prefer stable identifiers over dumping entire objects.

Avoid:

```java
log.info("Processing {}", hugeObject);
```

when a few fields provide enough context.

---

# 19. Validation

Validate input at the appropriate boundary.

Use Jakarta Bean Validation where appropriate:

```java
public record CreateCertificateRequest(
        @NotBlank String domain,
        @NotNull Environment environment
) {
}
```

Use:

```java
@Valid
@RequestBody
```

for request validation.

Do not rely only on client-side validation.

Business validation belongs in the appropriate business/service/domain layer.

---

# 20. Null Handling

Avoid unnecessary nulls.

Prefer explicit types and validation.

Use `Optional` primarily for return values where absence is meaningful.

Avoid:

```java
Optional<String> value
```

as entity fields or method parameters unless there is a strong reason.

Avoid deeply nested null checks.

Prefer:

```java
return repository.findById(id)
        .orElseThrow(() -> new CertificateNotFoundException(id));
```

over:

```java
Certificate certificate = repository.findById(id).orElse(null);

if (certificate == null) {
    ...
}
```

Do not use `Optional.get()` without checking presence.

---

# 21. String Handling

Avoid unnecessary string concatenation.

Prefer parameterized logging:

```java
log.info("Processing certificateId={}", certificateId);
```

instead of:

```java
log.info("Processing certificateId=" + certificateId);
```

For user-visible or domain messages, use clear wording.

Do not use magic strings repeatedly.

Extract constants when they genuinely represent a stable concept.

Do not create a constants class containing hundreds of unrelated strings.

---

# 22. Collections

Prefer interfaces:

```java
List<Certificate>
```

rather than:

```java
ArrayList<Certificate>
```

for fields, parameters, and return types unless the concrete type matters.

Use immutable factory methods where appropriate:

```java
List.of(...)
Set.of(...)
Map.of(...)
```

Avoid returning mutable collections unnecessarily.

Avoid modifying collections received from callers unless explicitly expected.

---

# 23. Streams

Use streams when they make code clearer.

Good:

```java
var activeCertificates = certificates.stream()
        .filter(Certificate::isActive)
        .toList();
```

Avoid overly complex streams.

Do not create a stream pipeline that is harder to understand than a simple loop.

Avoid side effects inside streams.

Do not use streams merely to demonstrate functional programming.

---

# 24. Loops

Traditional loops are perfectly acceptable.

Prefer readability over forcing streams.

Good:

```java
for (Certificate certificate : certificates) {
    process(certificate);
}
```

Do not replace straightforward code with complicated streams.

---

# 25. Methods

Methods should have:

* One clear responsibility
* Meaningful names
* Small scope
* Minimal side effects
* Clear inputs and outputs

Avoid methods with dozens or hundreds of lines.

Avoid excessive nesting.

Prefer guard clauses:

```java
if (!certificate.isRenewable()) {
    return;
}
```

instead of deeply nested logic.

Example:

```java
public void renew(Certificate certificate) {
    validateRenewable(certificate);

    var renewalResult = renewCertificate(certificate);

    persistResult(certificate, renewalResult);
}
```

rather than one giant method containing every concern.

---

# 26. Naming

Use descriptive names.

Prefer:

```java
certificateRepository
renewalClient
expirationDate
renewalWindow
```

Avoid:

```java
repo
client1
data
obj
temp
x
foo
```

Boolean names should communicate meaning:

```java
isActive
isExpired
hasRenewalPermission
canRenew
```

Methods should use verbs:

```java
renewCertificate()
findCertificate()
validateCertificate()
createCertificate()
```

---

# 27. Magic Numbers

Avoid unexplained magic numbers.

Bad:

```java
if (daysRemaining < 30) {
```

Prefer:

```java
private static final int RENEWAL_THRESHOLD_DAYS = 30;
```

or, preferably, configuration when the value is environment/business configurable.

---

# 28. Time and Dates

Use `java.time`.

Prefer:

```java
Instant
LocalDate
LocalDateTime
OffsetDateTime
ZonedDateTime
Duration
Period
```

Do not use legacy:

```java
java.util.Date
Calendar
```

unless required for compatibility.

Be explicit about time zones.

For distributed systems and persisted timestamps, prefer UTC/`Instant` where appropriate.

Never assume local server time.

---

# 29. HTTP Clients

Use the project's approved HTTP client.

Prefer Spring's modern HTTP client mechanisms where appropriate.

Configure:

* Connection timeout
* Read timeout
* Response timeout
* Retry behavior where appropriate

Do not retry blindly.

Only retry operations that are safe to retry or designed for idempotency.

Handle:

* HTTP status codes
* connection failures
* timeouts
* malformed responses
* authentication failures
* rate limits

Do not swallow HTTP errors.

---

# 30. AWS SDK Usage

When using AWS SDK:

* Use the AWS SDK version already managed by the project
* Prefer dependency injection for AWS clients where appropriate
* Do not hard-code credentials
* Do not hard-code regions unnecessarily
* Use IAM roles/standard AWS credential provider chains
* Avoid creating a new AWS client for every request
* Reuse clients
* Configure timeouts appropriately
* Handle AWS-specific exceptions
* Log meaningful AWS operation context without exposing credentials

Never log:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
session tokens
private keys
```

Prefer AWS IAM roles and workload identity mechanisms for deployed applications.

---

# 31. Secrets

Never commit secrets.

Never place secrets in:

* Java source
* `application.properties`
* `application.yml`
* test source
* Git
* Dockerfiles
* Terraform variables committed to source control
* logs

Use appropriate secret management mechanisms such as:

* AWS Secrets Manager
* AWS Systems Manager Parameter Store
* environment variables where appropriate
* approved enterprise secret management

Use placeholders in examples.

---

# 32. Transaction Management

Use `@Transactional` deliberately.

Do not put `@Transactional` everywhere.

Transactions should generally belong at the service/use-case boundary.

Example:

```java
@Transactional
public void renewCertificate(...) {
    ...
}
```

Understand transaction boundaries before adding the annotation.

Do not perform long-running external calls inside database transactions unless there is a specific reason.

For workflows involving external systems and databases, consider appropriate consistency patterns rather than assuming a single transaction can cover everything.

---

# 33. Concurrency

Do not introduce concurrency unnecessarily.

When concurrency is needed:

* Understand thread safety
* Avoid shared mutable state
* Prefer immutable objects
* Use appropriate Spring/AWS mechanisms
* Handle executor lifecycle correctly
* Avoid unbounded thread creation

Do not use:

```java
new Thread(...)
```

inside application business logic.

Prefer managed executors or Spring-supported asynchronous mechanisms.

---

# 34. Retry Logic

Retries must be intentional.

Before adding retries ask:

1. Is the operation safe to retry?
2. What errors should be retried?
3. How many attempts?
4. What backoff strategy?
5. What happens after all attempts fail?
6. Could retries amplify an outage?

Prefer exponential backoff where appropriate.

Avoid immediate infinite retries.

Log retries at an appropriate level.

---

# 35. Caching

Only introduce caching when there is a demonstrated need.

Before adding caching consider:

* Cache key
* TTL
* Invalidation
* Stale data
* Memory usage
* Distributed cache behavior
* Failure behavior

Do not add caching simply because it may be faster.

---

# 36. Security

Follow secure coding practices.

Always consider:

* Authentication
* Authorization
* Input validation
* Output encoding where relevant
* Injection attacks
* SSRF
* Path traversal
* Sensitive data exposure
* Broken access control
* Dependency vulnerabilities
* Secure headers
* TLS
* Secret management

Never trust external input.

Do not construct SQL queries using string concatenation.

Bad:

```java
"SELECT * FROM certificate WHERE domain = '" + domain + "'"
```

Use parameterized queries/repository mechanisms.

---

# 37. Error Messages

Error messages should be:

* Clear
* Actionable
* Safe
* Appropriate for the audience

Do not expose internal implementation details.

Bad:

```text
NullPointerException at CertificateRepositoryImpl.java:42
```

Better:

```text
Certificate could not be found.
```

Internal logs can contain more technical information than API responses.

---

# 38. Lombok

Use Lombok only where it provides meaningful value and is consistent with project standards.

Do not use Lombok to generate boilerplate when a Java record is the better solution.

Prefer:

```java
public record CertificateResponse(
        String id,
        String domain
) {
}
```

over a Lombok DTO containing:

```java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
```

Do not stack multiple Lombok annotations unnecessarily.

Avoid `@Data` blindly, especially on JPA entities.

Be careful with generated:

* `equals`
* `hashCode`
* `toString`

---

# 39. Builders

Do not use builders for every object.

A builder may be useful when:

* There are many optional parameters
* Construction is complex
* Readability genuinely improves
* The object has a complex creation process

For simple immutable data, prefer records:

```java
new CertificateResponse(id, domain, status);
```

Do not introduce a builder merely to avoid a constructor.

---

# 40. Interfaces

Do not create an interface for every service.

Avoid unnecessary:

```text
CertificateService
CertificateServiceImpl
```

when there is only one implementation and no architectural requirement for an interface.

A concrete service is often sufficient:

```java
@Service
public class CertificateService {
}
```

Use interfaces when they provide a meaningful boundary, such as:

* Multiple implementations
* External integration boundary
* Strategy pattern
* Plugin architecture
* Testing seam where genuinely useful
* Domain abstraction

---

# 41. Utility Classes

Avoid large generic utility classes.

Do not create:

```text
Utils.java
CommonUtils.java
Helper.java
MiscUtils.java
```

containing unrelated functionality.

Prefer domain-specific, well-named components.

Bad:

```java
StringUtils.doSomething(...)
```

when the operation belongs specifically to certificate processing.

---

# 42. Dependency Management

Keep dependencies minimal.

Before adding a dependency ask:

* Is it actually required?
* Does Spring Boot already provide the functionality?
* Can the JDK solve the problem?
* Is there already an existing project dependency?
* Does the dependency introduce security/licensing/maintenance concerns?

Do not add libraries for trivial functionality.

Keep dependency versions controlled by the project's dependency management.

---

# 43. Code Duplication

Avoid duplicated business logic.

If the same logic appears repeatedly, first determine whether it should be:

* extracted into a method
* extracted into a domain component
* shared through a meaningful service
* represented by a reusable value object

Do not create abstractions merely because two pieces of code look similar.

Duplication is sometimes preferable to an incorrect abstraction.

---

# 44. Dead Code

Do not leave:

* unused imports
* unused variables
* unreachable code
* commented-out old implementations
* obsolete TODOs
* unused methods
* unused dependencies

Remove obsolete code rather than commenting it out.

Git is the history mechanism.

---

# 45. TODOs

Do not add TODOs for obvious unfinished work unless the TODO represents a genuine future task.

Avoid:

```java
// TODO: fix this
```

Prefer a specific actionable TODO if genuinely required:

```java
// TODO: Replace polling with event-driven renewal when AWS event integration is available.
```

Do not leave unresolved TODOs after completing a task unless explicitly intended.

---

# 46. Comments

Code should be self-explanatory where possible.

Prefer meaningful names over comments.

Do not write comments that simply repeat the code.

Bad:

```java
// Increment counter
counter++;
```

Good comments explain:

* Why something is necessary
* Business constraints
* Non-obvious behavior
* External system limitations
* Important architectural decisions

---

# 47. Javadocs

Use Javadocs for:

* Public APIs
* Complex public interfaces
* Important domain concepts
* Non-obvious behavior

Do not generate meaningless Javadocs for every private method.

Bad:

```java
/**
 * Gets the certificate.
 */
public Certificate getCertificate() {
```

Good:

```java
/**
 * Determines whether the certificate is eligible for automatic renewal
 * based on the configured renewal window and current validity period.
 */
```

---

# 48. Testing Philosophy

Tests are part of the implementation, not an afterthought.

Every meaningful feature should have appropriate automated tests.

Prefer the testing pyramid:

```text
          E2E
        /     \
   Integration
      /       \
       Unit
```

Use:

* Unit tests for business logic
* Integration tests for Spring/database/infrastructure integration
* API tests for REST behavior
* End-to-end tests only where genuinely valuable

Do not write tests simply to increase coverage numbers.

Tests must verify behavior.

---

# 49. JUnit 5

Use JUnit 5.

Prefer:

```java
import org.junit.jupiter.api.Test;
```

Use:

```java
@BeforeEach
@AfterEach
@BeforeAll
@AfterAll
@Nested
@DisplayName
@ParameterizedTest
```

where useful.

Keep tests readable.

---

# 50. Test Naming

Test names must explain behavior.

Prefer:

```java
shouldReturnCertificateWhenCertificateExists()
```

```java
shouldThrowCertificateNotFoundExceptionWhenCertificateDoesNotExist()
```

```java
shouldRenewCertificateWhenExpirationIsWithinRenewalWindow()
```

Avoid:

```java
test1()
testCertificate()
testMethod()
```

Use a consistent naming convention across the project.

---

# 51. Given / When / Then

Structure tests clearly:

```java
@Test
void shouldRenewCertificateWhenWithinRenewalWindow() {
    // Given
    var certificate = certificateExpiringSoon();

    // When
    var result = service.renew(certificate);

    // Then
    assertThat(result.status()).isEqualTo(RENEWED);
}
```

The exact assertion library may follow project standards.

Tests should clearly communicate:

* Setup
* Action
* Expected result

---

# 52. AssertJ

If AssertJ is available, prefer it for readable assertions.

Prefer:

```java
assertThat(result.status()).isEqualTo(RENEWED);
```

instead of unnecessarily verbose assertions.

For collections:

```java
assertThat(results)
        .hasSize(2)
        .extracting(CertificateResponse::status)
        .containsExactly(ACTIVE, EXPIRED);
```

Use assertions that explain failures clearly.

---

# 53. Mockito

Use Mockito where mocking is appropriate.

Example:

```java
@Mock
private CertificateRepository repository;

@InjectMocks
private CertificateService service;
```

Do not mock everything.

Mock external dependencies and collaborators where appropriate.

Do not mock simple value objects.

Avoid over-mocking implementation details.

---

# 54. Unit Tests

Unit tests should be:

* Fast
* Isolated
* Deterministic
* Focused
* Easy to understand

A unit test should not require:

* Real AWS
* Real database
* Real network
* Real external API

unless it is specifically an integration test.

---

# 55. Spring Boot Tests

Use Spring context tests only when Spring integration needs to be verified.

Examples:

```java
@SpringBootTest
```

```java
@WebMvcTest
```

```java
@DataJpaTest
```

Do not use `@SpringBootTest` for every unit test.

It makes tests slower and less isolated.

Choose the smallest appropriate test slice.

---

# 56. Integration Tests

Integration tests should verify real integration between components.

Examples:

* Repository + database
* Controller + service
* Service + external client
* AWS integration
* Configuration
* Serialization/deserialization

Where appropriate, use Testcontainers rather than assuming local infrastructure.

---

# 57. Testcontainers

Use Testcontainers where realistic infrastructure is required and supported by the project.

Examples:

* PostgreSQL
* MySQL
* LocalStack
* Kafka
* Redis

Do not introduce Testcontainers unnecessarily.

---

# 58. REST Controller Tests

Test:

* HTTP status
* Request validation
* Response structure
* Error handling
* Authorization where relevant
* Serialization
* Content type

Example scenarios:

```text
valid request -> 201
invalid request -> 400
missing resource -> 404
unauthorized -> 401
forbidden -> 403
conflict -> 409
```

Do not only test successful requests.

---

# 59. Service Tests

Test business behavior.

Include:

* Happy path
* Boundary conditions
* Invalid input
* Missing data
* External failures
* Retry behavior where applicable
* State transitions
* Error conditions

For a renewal service, examples include:

```text
certificate is valid -> no renewal
certificate expires soon -> renewal
certificate already expired -> expected handling
renewal succeeds -> persisted
renewal fails -> expected error
external service unavailable -> expected behavior
```

---

# 60. Parameterized Tests

Use parameterized tests when the same behavior should be tested against multiple inputs.

Example:

```java
@ParameterizedTest
@ValueSource(ints = {0, 1, 7, 30})
void shouldIdentifyCertificatesWithinRenewalWindow(int days) {
    ...
}
```

Do not duplicate nearly identical tests.

---

# 61. Boundary Testing

Always consider boundaries.

Examples:

```text
0 days
1 day
renewal threshold - 1
renewal threshold
renewal threshold + 1
```

Test:

* Empty collections
* Null/invalid input where relevant
* Maximum/minimum values
* Time boundaries
* Pagination boundaries
* Retry limits

---

# 62. Exception Testing

Verify both:

* Exception type
* Meaningful behavior/message where appropriate

Example:

```java
var exception = assertThrows(
        CertificateNotFoundException.class,
        () -> service.findById("missing")
);

assertThat(exception.getMessage())
        .contains("missing");
```

Do not assert excessively on implementation details.

---

# 63. Test Data

Create readable test data.

Prefer factory methods:

```java
private Certificate certificateExpiringIn(int days) {
    return ...
}
```

Avoid massive setup blocks repeated across tests.

Do not create huge fixture classes unless necessary.

Use realistic data.

Do not use production secrets or production customer data in tests.

---

# 64. Mock Verification

Verify interactions only when they are behaviorally meaningful.

Avoid:

```java
verify(repository).findById(id);
verify(repository).save(any());
verify(service).method();
```

for every implementation detail.

Tests should remain valid after harmless refactoring.

Prefer verifying observable outcomes.

---

# 65. Code Coverage

Coverage is useful but is not the goal.

Do not write meaningless tests simply to reach:

```text
100% coverage
```

Prioritize coverage of:

* Business rules
* Error paths
* Security-sensitive code
* Important integrations
* Boundary conditions

---

# 66. Mutation Testing

Where the project supports it, mutation testing may be used to identify weak tests.

A test suite should fail when meaningful production behavior changes.

---

# 67. Test Maintainability

Tests are production-quality code too.

Apply the same standards:

* Clear names
* Small methods
* No duplication where harmful
* No unnecessary abstraction
* No magic values
* Meaningful assertions
* Deterministic behavior

Avoid flaky tests.

Never use arbitrary sleeps such as:

```java
Thread.sleep(5000);
```

Use proper synchronization, polling, Awaitility, or framework-supported mechanisms where appropriate.

---

# 68. Static Analysis

Keep code compatible with configured tools such as:

* Checkstyle
* SpotBugs
* PMD
* SonarQube/SonarCloud
* Error Prone
* OWASP dependency checks

Do not suppress warnings without understanding them.

If a suppression is genuinely required, document why.

---

# 69. Formatting

Follow the project's existing formatting rules.

Do not introduce a new formatting style.

Keep:

* indentation consistent
* imports clean
* line lengths reasonable
* braces consistent
* whitespace clean

Use the project's formatter if available.

---

# 70. Build Verification

After making code changes:

1. Compile
2. Run unit tests
3. Run relevant integration tests
4. Run static analysis where configured
5. Verify formatting
6. Review changed files

Do not assume code is correct simply because it compiles.

---

# 71. Dependency and Build Changes

When modifying:

```text
pom.xml
build.gradle
settings.gradle
gradle.properties
```

consider:

* Dependency version compatibility
* Transitive dependencies
* Security vulnerabilities
* Build reproducibility
* Java version compatibility
* Spring Boot compatibility

Do not upgrade unrelated dependencies during a feature change unless necessary.

---

# 72. Refactoring

When refactoring:

* Preserve existing behavior unless behavior change is intended
* Keep changes focused
* Avoid mixing unrelated refactoring with feature work
* Add/update tests first where useful
* Remove duplication carefully
* Avoid massive rewrites

Prefer incremental refactoring.

If an existing implementation works but is unnecessarily complex, simplify it.

---

# 73. Code Review Mindset

Before considering work complete, review the code as if performing a production code review.

Ask:

### Correctness

* Does it actually solve the requirement?
* Are edge cases handled?
* Are failure paths handled?

### Maintainability

* Is the code easy to understand?
* Are classes too large?
* Are methods too complex?
* Is there duplication?

### Design

* Is responsibility in the correct layer?
* Is the abstraction justified?
* Are dependencies clear?

### Testing

* Are important behaviors covered?
* Are failure paths tested?
* Are boundary cases tested?

### Security

* Is user input validated?
* Could secrets leak?
* Are permissions checked?
* Is sensitive data protected?

### Logging

* Are important events logged?
* Are failures logged?
* Is sensitive information excluded?
* Are logs actionable?

---

# 74. Complexity

Avoid unnecessary cyclomatic complexity.

If logic becomes deeply nested:

```java
if (...) {
    if (...) {
        if (...) {
            if (...) {
            }
        }
    }
}
```

consider:

* Guard clauses
* Extracting methods
* Domain methods
* Strategy pattern only if genuinely justified

Do not create abstractions solely to reduce line count.

---

# 75. Switch Expressions

Prefer modern switch expressions where they improve clarity.

Example:

```java
return switch (status) {
    case ACTIVE -> processActive();
    case EXPIRED -> processExpired();
    case REVOKED -> processRevoked();
};
```

Avoid large chains of `if/else` when a clear switch is more appropriate.

---

# 76. Enums

Use enums for finite domain states.

Example:

```java
public enum CertificateStatus {
    ACTIVE,
    EXPIRING,
    EXPIRED,
    REVOKED
}
```

Do not represent fixed domain states as arbitrary strings throughout the application.

---

# 77. Value Objects

Where a domain concept has validation or behavior, consider a value object.

For simple immutable values, records are preferred.

Example:

```java
public record DomainName(String value) {

    public DomainName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Domain name cannot be blank");
        }
    }
}
```

Do not create value objects for trivial values without a real benefit.

---

# 78. Mapping Between Layers

Do not expose persistence entities directly when the API contract should be independent of persistence.

Use explicit mapping where appropriate:

```java
public CertificateResponse toResponse(Certificate certificate) {
    return new CertificateResponse(
            certificate.getId(),
            certificate.getDomain(),
            certificate.getStatus().name()
    );
}
```

Avoid huge mapping frameworks unless the project genuinely benefits from them.

Keep mapping predictable and testable.

---

# 79. API Compatibility

When changing API contracts:

* Consider backward compatibility
* Avoid unnecessary breaking changes
* Version APIs where appropriate
* Update documentation
* Update tests
* Update clients if applicable

Do not silently change response structures.

---

# 80. Database Migrations

Use the project's migration mechanism, such as:

* Flyway
* Liquibase

Never manually modify production schemas as part of normal application deployment unless explicitly required by the project's process.

Migration scripts should be:

* Versioned
* Reviewable
* Repeatable where appropriate
* Tested

Do not modify an already-applied migration casually.

Create a new migration for changes.

---

# 81. Database Performance

Avoid:

* N+1 queries
* Full table scans when avoidable
* Loading unnecessary columns
* Loading massive collections
* Unbounded queries

Consider:

* indexes
* pagination
* projections
* appropriate query design

Do not optimize without evidence.

---

# 82. API Pagination

For potentially large result sets, use pagination.

Do not return an unlimited number of database records from an API.

Use appropriate Spring Data pagination mechanisms.

---

# 83. External Service Failures

External systems will fail.

Design for:

* Timeouts
* Connection failures
* Rate limiting
* Partial failures
* Invalid responses
* Authentication failures
* Service outages

Do not assume external APIs always succeed.

Failures should be observable through logs/metrics.

---

# 84. Idempotency

For operations that may be retried, consider idempotency.

Especially important for:

* Certificate renewal
* Resource creation
* AWS operations
* Payment-like operations
* Message processing

Do not perform duplicate side effects simply because a request was retried.

---

# 85. Scheduled Jobs

For scheduled jobs:

* Keep execution logic in a service
* Keep scheduler methods thin
* Log start/end/failure
* Prevent unintended overlapping executions where required
* Consider distributed locking in multi-instance deployments
* Handle partial failures
* Make jobs restartable where possible

Example:

```java
@Scheduled(...)
public void renewCertificates() {
    certificateRenewalService.renewDueCertificates();
}
```

Do not put hundreds of lines of logic inside the scheduled method.

---

# 86. Batch Processing

For batch operations:

* Process manageable chunks
* Avoid loading millions of records into memory
* Handle individual failures appropriately
* Provide meaningful progress/metrics where appropriate
* Make processing restartable where practical

Do not allow one bad record to unintentionally terminate the entire batch unless that is the desired behavior.

---

# 87. Observability

Production services should provide appropriate:

* Logs
* Metrics
* Health checks
* Traces where applicable

Use Spring Boot Actuator when appropriate.

Useful metrics may include:

```text
certificate_renewal_success_total
certificate_renewal_failure_total
certificate_renewal_duration
external_api_failure_total
```

Do not add hundreds of meaningless metrics.

---

# 88. Health Checks

Health checks should indicate whether critical dependencies are available when appropriate.

Do not make health checks perform expensive operations.

Do not expose sensitive diagnostic information through public health endpoints.

---

# 89. Graceful Failure

Applications should fail safely.

When an external dependency is unavailable:

* Fail clearly
* Log appropriately
* Avoid infinite retries
* Avoid corrupting state
* Return appropriate API responses
* Allow recovery when the dependency becomes available

---

# 90. Resource Management

Always properly manage:

* Files
* Streams
* HTTP connections
* Database resources
* Executors
* AWS resources

Prefer try-with-resources:

```java
try (var inputStream = resource.getInputStream()) {
    ...
}
```

Avoid manual resource cleanup where Java/Spring can manage it safely.

---

# 91. Serialization

Be deliberate about JSON serialization.

Use explicit API DTOs.

Do not accidentally expose internal fields.

Be careful with:

* Dates
* Enums
* Null values
* Sensitive fields
* Nested objects

Keep API contracts stable.

---

# 92. Configuration Profiles

Use Spring profiles carefully.

Typical profiles might include:

```text
local
test
dev
qa
prod
```

Do not duplicate huge configuration files unnecessarily.

Never store production secrets in profile configuration committed to Git.

---

# 93. Environment Independence

The same application artifact should ideally be deployable across environments with configuration changes rather than source-code changes.

Avoid:

```java
if (environment.equals("prod")) {
    ...
}
```

unless genuinely required.

Prefer externalized configuration.

---

# 94. Docker

If Docker is used:

* Use minimal appropriate base images
* Do not run as root unnecessarily
* Avoid embedding secrets
* Keep image layers efficient
* Use `.dockerignore`
* Pin important dependencies appropriately
* Perform security scanning

Do not place credentials inside Dockerfiles.

---

# 95. Documentation

Update documentation when behavior changes.

Documentation may include:

* README
* API documentation
* Architecture documentation
* Configuration documentation
* Runbook
* Deployment instructions

Do not create documentation that duplicates code unnecessarily.

---

# 96. OpenAPI

If OpenAPI/Swagger is used:

* Document public endpoints
* Document request/response schemas
* Document important error responses
* Keep descriptions meaningful
* Avoid exposing internal models

Do not manually duplicate API definitions if they can be generated reliably.

---

# 97. Git Practices

Keep commits focused.

Avoid mixing:

```text
feature
mass formatting
unrelated refactoring
dependency upgrades
```

in one change.

Before completing a task:

* Review `git diff`
* Remove accidental changes
* Remove debug statements
* Remove temporary files
* Remove secrets
* Ensure only relevant files changed

Never commit credentials or secrets.

---

# 98. Claude Code Workflow

When implementing a task, Claude should follow this general workflow:

### Step 1 — Understand

Read:

* Existing source code
* Relevant tests
* Build configuration
* Configuration files
* Existing architecture
* Existing coding conventions

Do not immediately rewrite code.

### Step 2 — Plan

Identify:

* Required changes
* Affected components
* Existing reusable functionality
* Required tests
* Potential risks

Prefer the smallest change that correctly solves the problem.

### Step 3 — Implement

Implement incrementally.

Follow existing project conventions unless they conflict with these standards.

### Step 4 — Test

Add or update tests.

Run relevant tests.

### Step 5 — Review

Review:

* Correctness
* Maintainability
* Security
* Logging
* Exception handling
* Test quality
* Duplication
* Complexity

### Step 6 — Verify

Run appropriate:

```text
compile
unit tests
integration tests
static analysis
formatting
```

Do not claim a test passed unless it was actually executed successfully.

---

# 99. Before Adding a New Class

Before creating a new class, ask:

1. Is the class actually necessary?
2. Does an existing class already own this responsibility?
3. Can a record solve this without boilerplate?
4. Can an existing abstraction be reused?
5. Is the new abstraction making the design clearer?

Avoid creating:

```text
FooManager
FooHelper
FooUtils
FooProcessor
FooHandler
FooServiceImpl
```

without a clear responsibility.

---

# 100. Before Adding a New Dependency

Ask:

1. Can Java solve this?
2. Can Spring Boot solve this?
3. Is the functionality already available?
4. Is the dependency maintained?
5. Does it introduce security risk?
6. Is the dependency worth the additional maintenance?

---

# 101. Before Adding Logging

Ask:

1. Is this event useful operationally?
2. Is the log level appropriate?
3. Could the message expose sensitive data?
4. Does the message contain enough context?
5. Am I logging the same error multiple times?

Logs should help an engineer diagnose production behavior.

---

# 102. Before Catching an Exception

Ask:

1. Can this exception be handled meaningfully here?
2. Should it propagate?
3. Should it be translated into a domain exception?
4. Should it trigger retry?
5. Should it be logged here or at a higher boundary?

Never do:

```java
catch (Exception e) {
    // ignore
}
```

Never silently swallow exceptions.

If an exception is intentionally ignored, document the reason clearly.

---

# 103. Avoid Overengineering

Do not automatically introduce:

* Factory patterns
* Strategy patterns
* Abstract factories
* Generic frameworks
* Event buses
* Complex dependency injection
* Multiple interfaces
* Multiple layers
* Custom infrastructure

unless there is a real requirement.

Simple code is preferred.

For example, do not turn:

```java
return certificateRepository.findById(id);
```

into five classes simply to satisfy an abstract architecture.

Architecture should serve the application.

---

# 104. Prefer Readability Over Cleverness

Avoid clever one-liners when they reduce readability.

Bad:

```java
return Optional.ofNullable(a).map(A::getB).map(B::getC).orElse(null);
```

if a simple implementation is easier to understand.

Readable code is better than compact code.

---

# 105. Minimize Boilerplate

Always look for opportunities to reduce unnecessary boilerplate.

Preferred order:

1. Java language features
2. Spring Boot capabilities
3. Existing project utilities
4. Small explicit implementations
5. Libraries only when justified

Use records aggressively for simple immutable data.

Do not create traditional DTOs containing dozens of lines when a record provides the same behavior safely.

---

# 106. Production Readiness Checklist

Before considering a feature complete, verify:

## Code

* [ ] Code compiles
* [ ] Code is readable
* [ ] Classes have clear responsibilities
* [ ] Methods are reasonably small
* [ ] No unnecessary abstractions
* [ ] No unnecessary boilerplate
* [ ] Records used where appropriate
* [ ] No dead code
* [ ] No duplicated business logic
* [ ] No magic values without justification

## Spring

* [ ] Constructor injection
* [ ] Appropriate bean scopes
* [ ] Configuration externalized
* [ ] Transactions used appropriately
* [ ] Controllers remain thin

## Error Handling

* [ ] Meaningful exceptions
* [ ] Appropriate HTTP status codes
* [ ] Global exception handling where appropriate
* [ ] No swallowed exceptions
* [ ] No internal stack traces exposed

## Logging

* [ ] Important operations logged
* [ ] Failures logged
* [ ] Correct log levels
* [ ] Useful identifiers included
* [ ] No passwords/secrets/tokens/private keys logged
* [ ] No excessive logging

## Testing

* [ ] Unit tests added/updated
* [ ] Happy path tested
* [ ] Error path tested
* [ ] Boundary conditions tested
* [ ] Validation tested
* [ ] Integration tests added where necessary
* [ ] No flaky tests
* [ ] Tests verify behavior rather than implementation details

## Security

* [ ] Input validated
* [ ] Authorization considered
* [ ] Secrets protected
* [ ] No SQL injection
* [ ] No sensitive information in logs
* [ ] Dependencies checked
* [ ] External inputs treated as untrusted

## Operations

* [ ] Appropriate metrics
* [ ] Health checks where needed
* [ ] Timeouts configured
* [ ] Retry behavior considered
* [ ] External failures handled
* [ ] Observability considered

---

# 107. Final Rule

When choosing between two implementations, prefer the implementation that is:

* Easier to understand
* Easier to test
* Easier to operate
* Easier to change
* More secure
* Less coupled
* Less repetitive
* Less dependent on unnecessary libraries
* Less dependent on unnecessary abstractions
* More idiomatic Java/Spring Boot
* Explicit about failure behavior

**Do not write code merely because it works. Write code that another engineer can safely understand, test, operate, and modify several years from now.**

Always favor **clean, simple, production-ready Java over clever or unnecessarily complex code.**
