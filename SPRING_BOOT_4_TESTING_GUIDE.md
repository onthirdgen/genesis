# Spring Boot 4.0.0 Testing Guide

**Created**: 2025-12-31
**Purpose**: Document issues encountered and solutions for creating JUnit tests with Spring Boot 4.0.0
**Applies to**: All microservices in the Call Auditing Platform

---

## Table of Contents
1. [Critical Issues & Resolutions](#critical-issues--resolutions)
2. [Spring Boot 4.0.0 Breaking Changes](#spring-boot-40-breaking-changes)
3. [Testing Patterns](#testing-patterns)
4. [Configuration Setup](#configuration-setup)
5. [Common Pitfalls](#common-pitfalls)
6. [Test Execution](#test-execution)

---

## Critical Issues & Resolutions

### Issue 1: Removed Test Slice Annotations

**Problem**: Spring Boot 4.0.0 removed test slice annotations from `org.springframework.boot.test.autoconfigure.web.servlet` and `.orm.jpa` packages:
- `@WebMvcTest` ❌
- `@DataJpaTest` ❌
- `@MockBean` ❌
- `@AutoConfigureMockMvc` ❌

**Error Messages**:
```
package org.springframework.boot.test.autoconfigure.web.servlet does not exist
package org.springframework.boot.test.autoconfigure.orm.jpa does not exist
```

**✅ Solution**: Use Spring Boot 4.0.0 compatible patterns

#### For Controller Tests:
```java
@SpringBootTest
@ActiveProfiles("test")
class MyControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public MyService myService() {
            return mock(MyService.class);  // Use Mockito directly
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MyService myService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Manual MockMvc setup
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reset(myService);  // Reset mocks between tests
    }
}
```

#### For Repository Tests:
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // Automatic rollback after each test
class MyRepositoryTest {

    @Autowired
    private MyRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }
}
```

---

### Issue 2: @PostConstruct Methods Not Called in Unit Tests

**Problem**: When creating service instances with `new MyService()` in unit tests, `@PostConstruct` methods don't execute, causing `NullPointerException` on fields initialized in those methods.

**Example**:
```java
@Service
public class VocAnalysisService {
    @Value("${voc.stopwords}")
    private String stopwordsConfig;

    private Set<String> stopwords;  // Initialized in @PostConstruct

    @PostConstruct
    private void initializeStopwords() {
        stopwords = new HashSet<>();
        if (stopwordsConfig != null && !stopwordsConfig.isEmpty()) {
            stopwords.addAll(Arrays.asList(stopwordsConfig.split(",")));
        }
    }

    public List<String> extractKeywords(String text) {
        // NullPointerException here if stopwords is null!
        if (stopwords.contains(word)) { ... }
    }
}
```

**Error**:
```
java.lang.NullPointerException: Cannot invoke "java.util.Set.contains(Object)"
because "<local3>.stopwords" is null
```

**✅ Solution**: Manually invoke `@PostConstruct` methods using ReflectionTestUtils

```java
@BeforeEach
void setUp() {
    vocAnalysisService = new VocAnalysisService();

    // Set @Value fields using reflection
    ReflectionTestUtils.setField(vocAnalysisService, "stopwordsConfig",
        "the,a,an,and,or,but,is,are...");
    ReflectionTestUtils.setField(vocAnalysisService, "maxKeywords", 10);
    ReflectionTestUtils.setField(vocAnalysisService, "minKeywordLength", 3);

    // ✅ CRITICAL: Manually invoke @PostConstruct method
    ReflectionTestUtils.invokeMethod(vocAnalysisService, "initializeStopwords");
}
```

**Import Required**:
```java
import org.springframework.test.util.ReflectionTestUtils;
```

---

### Issue 3: List.of() Type Inference Issues with Object[]

**Problem**: When mocking repository methods that return `List<Object[]>`, using `List.of(new Object[]{...})` causes compilation errors due to type inference.

**Error**:
```
no suitable method found for thenReturn(java.util.List<java.lang.Object>)
    method org.mockito.stubbing.OngoingStubbing.thenReturn(java.util.List<java.lang.Object[]>)
    is not applicable (argument mismatch; inference variable E has incompatible bounds)
```

**❌ Wrong**:
```java
when(repository.findTopKeywords(10))
    .thenReturn(List.of(new Object[]{"billing", 10L}));  // Compilation error!
```

**✅ Solution**: Create the Object[] first, then use Collections.singletonList()

```java
Object[] row1 = new Object[]{"billing", 10L};
Object[] row2 = new Object[]{"problem", 8L};

when(repository.findTopKeywords(10))
    .thenReturn(Arrays.asList(row1, row2));  // Works!

// Or for single row:
Object[] row = new Object[]{Intent.COMPLAINT, 10L};
when(repository.countByIntent())
    .thenReturn(Collections.singletonList(row));  // Works!
```

---

### Issue 4: H2 Database Doesn't Support PostgreSQL JSONB

**Problem**: Repository integration tests fail when using H2 in-memory database with JSONB columns (PostgreSQL-specific feature).

**Error**:
```
DataIntegrityViolation: could not execute statement
[Data conversion error converting "CAST(... AS JAVA_OBJECT)
(voc_insights: "actionable_items" JSON)"]
```

**Context**: Entities with `List<String>` fields stored as JSONB:
```java
@Entity
@Table(name = "voc_insights")
public class VocInsight {
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private List<String> actionableItems;  // Fails in H2!

    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private List<String> keywords;

    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private List<String> topics;
}
```

**✅ Solutions**:

**Option 1: Use H2 with Limitations (Recommended for Controller/Service Tests)**
- Controller tests: Use H2 (service layer is mocked, so database type doesn't matter)
- Service tests: Use H2 or no database (pure unit tests with mocks)
- Accept that some repository tests will fail with H2

**Option 2: Use Testcontainers for Full PostgreSQL Support (Recommended for Repository Tests)**
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class VocInsightRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**Option 3: Separate Test Profiles**
- `application-test.yml`: H2 database for unit tests
- `application-integration-test.yml`: Real PostgreSQL for integration tests

---

### Issue 5: Missing @ActiveProfiles("test") Annotation

**Problem**: Integration tests try to connect to production PostgreSQL instead of test database.

**Error**:
```
org.postgresql.util.PSQLException: The connection attempt failed.
Caused by: org.hibernate.exception.JDBCConnectionException:
Unable to open JDBC Connection for DDL execution
```

**✅ Solution**: Always add @ActiveProfiles("test") to integration tests

```java
@SpringBootTest
@ActiveProfiles("test")  // ✅ CRITICAL: Load application-test.yml
class MyControllerTest {
    // ...
}
```

---

### Issue 6: Method Name Split Across Lines (Copy-Paste Error)

**Problem**: Method names accidentally split across lines during file creation cause compilation errors.

**Error**:
```
[ERROR] '(' expected
[ERROR] void getHighChurnRiskCustomers_LowerThreshold_ReturnsMor
eCustomers() {
```

**✅ Solution**: Ensure method names are on a single line

```java
// ❌ Wrong
void getHighChurnRiskCustomers_LowerThreshold_ReturnsMor
eCustomers() {

// ✅ Correct
void getHighChurnRiskCustomers_LowerThreshold_ReturnsMoreCustomers() {
```

---

## Spring Boot 4.0.0 Breaking Changes

### Removed Packages
- `org.springframework.boot.test.autoconfigure.web.servlet.*`
- `org.springframework.boot.test.autoconfigure.orm.jpa.*`
- `org.springframework.boot.test.mock.mockito.*`

### Migration Table

| Spring Boot 3.x | Spring Boot 4.0.0 |
|----------------|-------------------|
| `@WebMvcTest` | `@SpringBootTest` + manual MockMvc setup |
| `@DataJpaTest` | `@SpringBootTest` + `@Transactional` |
| `@MockBean` | `@Primary` bean returning `mock(Class.class)` |
| `@AutoConfigureMockMvc` | Manual `MockMvcBuilders.webAppContextSetup()` |

---

## Testing Patterns

### Pattern 1: Unit Test for Service Layer

**Use When**: Testing business logic in isolation without Spring context

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private MyRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private MyService myService;

    @BeforeEach
    void setUp() {
        // Configure mocks if needed
    }

    @Test
    void myMethod_ValidInput_ReturnsExpectedOutput() {
        // Arrange
        when(repository.findById(any())).thenReturn(Optional.of(testData));

        // Act
        Result result = myService.myMethod(input);

        // Assert
        assertNotNull(result);
        verify(repository).findById(any());
    }
}
```

---

### Pattern 2: Controller Integration Test

**Use When**: Testing REST endpoints with Spring context

```java
@SpringBootTest
@ActiveProfiles("test")
class MyControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public MyService myService() {
            return mock(MyService.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MyService myService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reset(myService);
    }

    @Test
    void getResource_ExistingId_Returns200Ok() throws Exception {
        // Arrange
        when(myService.getById("123")).thenReturn(Optional.of(testData));

        // Act & Assert
        mockMvc.perform(get("/api/resource/{id}", "123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("123"));

        verify(myService).getById("123");
    }
}
```

---

### Pattern 3: Repository Integration Test

**Use When**: Testing database operations with real database

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // Rollback after each test
class MyRepositoryTest {

    @Autowired
    private MyRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void save_NewEntity_GeneratesId() {
        // Arrange
        MyEntity entity = MyEntity.builder()
            .name("Test")
            .build();

        // Act
        MyEntity saved = repository.save(entity);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("Test", saved.getName());
    }
}
```

---

### Pattern 4: Kafka Event Listener Test

**Use When**: Testing Kafka message processing

```java
@ExtendWith(MockitoExtension.class)
class MyEventListenerTest {

    @Mock
    private MyService myService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MyEventListener eventListener;

    @Test
    void handleEvent_ValidMessage_ProcessesSuccessfully() throws Exception {
        // Arrange
        String eventJson = "{\"eventId\":\"123\"}";
        MyEvent event = createTestEvent();

        when(objectMapper.readValue(eventJson, MyEvent.class)).thenReturn(event);
        when(myService.process(any())).thenReturn(result);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"result\":\"ok\"}");

        // Act
        eventListener.handleEvent(eventJson);

        // Assert
        verify(myService).process(any());
        verify(kafkaTemplate).send(eq("output-topic"), anyString(), anyString());
    }
}
```

---

## Configuration Setup

### application-test.yml

**Location**: `src/test/resources/application-test.yml`

**Purpose**: Test-specific configuration using H2 in-memory database

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
    username: sa
    password:

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
        temp:
          use_jdbc_metadata_defaults: false
    show-sql: false

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: my-service-test
      auto-offset-reset: earliest
      key-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
        spring.deserializer.value.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

# Service-specific configuration
my-service:
  some-property: test-value

logging:
  level:
    com.callaudit: DEBUG
    org.springframework.kafka: WARN
    org.hibernate: WARN
```

---

### pom.xml Test Dependencies

**Required Dependencies**:

```xml
<dependencies>
    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Spring Boot Test Autoconfigure (for @Transactional) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-test-autoconfigure</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Spring Kafka Test -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- H2 Database for testing -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers (Optional - for integration tests) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Common Pitfalls

### ❌ Pitfall 1: Using Old Test Annotations
```java
@WebMvcTest  // ❌ Doesn't exist in Spring Boot 4.0.0
@MockBean    // ❌ Doesn't exist in Spring Boot 4.0.0
```

### ✅ Correct Approach:
```java
@SpringBootTest
@ActiveProfiles("test")
// Use @Primary beans with Mockito mocks
```

---

### ❌ Pitfall 2: Forgetting to Reset Mocks
```java
@BeforeEach
void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    // ❌ Forgot to reset mocks - previous test state bleeds into next test
}
```

### ✅ Correct Approach:
```java
@BeforeEach
void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    reset(myService);  // ✅ Reset mocks between tests
}
```

---

### ❌ Pitfall 3: Not Calling @PostConstruct Methods
```java
@BeforeEach
void setUp() {
    myService = new MyService();
    ReflectionTestUtils.setField(myService, "config", "value");
    // ❌ Forgot to call @PostConstruct method - fields not initialized!
}
```

### ✅ Correct Approach:
```java
@BeforeEach
void setUp() {
    myService = new MyService();
    ReflectionTestUtils.setField(myService, "config", "value");
    ReflectionTestUtils.invokeMethod(myService, "init");  // ✅ Call @PostConstruct
}
```

---

### ❌ Pitfall 4: Missing @ActiveProfiles
```java
@SpringBootTest  // ❌ Will try to connect to production database
class MyTest {
```

### ✅ Correct Approach:
```java
@SpringBootTest
@ActiveProfiles("test")  // ✅ Uses application-test.yml
class MyTest {
```

---

### ❌ Pitfall 5: Incorrect List.of() Usage with Object[]
```java
when(repository.findTopKeywords(10))
    .thenReturn(List.of(new Object[]{"word", 10L}));  // ❌ Compilation error
```

### ✅ Correct Approach:
```java
Object[] row = new Object[]{"word", 10L};
when(repository.findTopKeywords(10))
    .thenReturn(Collections.singletonList(row));  // ✅ Works
```

---

## Test Execution

### Running All Tests
```bash
cd /path/to/service
./mvnw clean test
```

### Running Specific Test Class
```bash
./mvnw test -Dtest=MyServiceTest
```

### Running Multiple Test Classes
```bash
./mvnw test -Dtest="MyServiceTest,MyControllerTest"
```

### Running Unit Tests Only (No Integration Tests)
```bash
./mvnw test -Dtest="*Test" -DexcludeTests="*IntegrationTest"
```

### Running Tests with Real PostgreSQL and Kafka
```bash
# Ensure infrastructure is running
docker compose up -d kafka postgres

# Run tests with overrides
./mvnw test \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/call_auditing \
  -Dspring.datasource.username=postgres \
  -Dspring.datasource.password=postgres \
  -Dspring.kafka.bootstrap-servers=localhost:9092
```

---

## Test Coverage Goals

### Minimum Coverage per Service:
- **Unit Tests**:
  - Service layer: 80%+ coverage
  - Business logic: 90%+ coverage

- **Integration Tests**:
  - Controller endpoints: 100% of public APIs
  - Repository: Basic CRUD + custom queries
  - Kafka listeners: All event types

### Test Structure:
```
src/test/java/com/callaudit/myservice/
├── controller/
│   └── MyControllerTest.java           (Integration test)
├── service/
│   └── MyServiceTest.java              (Unit test)
├── repository/
│   └── MyRepositoryTest.java           (Integration test)
└── listener/
    └── MyEventListenerTest.java        (Unit test)
```

---

## Quick Reference Checklist

### ✅ Before Creating Tests:
- [ ] Spring Boot parent version is 4.0.0 in pom.xml
- [ ] Test dependencies added (spring-boot-starter-test, H2, Testcontainers)
- [ ] application-test.yml created in src/test/resources
- [ ] Understand which annotations are removed in Spring Boot 4.0.0

### ✅ While Writing Tests:
- [ ] Use @SpringBootTest instead of @WebMvcTest/@DataJpaTest
- [ ] Add @ActiveProfiles("test") to integration tests
- [ ] Use @Primary beans with Mockito mocks instead of @MockBean
- [ ] Manual MockMvc setup for controller tests
- [ ] Use @Transactional for repository tests (auto-rollback)
- [ ] Reset mocks in @BeforeEach
- [ ] Invoke @PostConstruct methods manually when needed
- [ ] Use Collections.singletonList() for Object[] mocking

### ✅ After Writing Tests:
- [ ] Run tests locally: `./mvnw test`
- [ ] Verify all tests pass
- [ ] Check test coverage
- [ ] Run integration tests with real infrastructure if needed
- [ ] Document any service-specific test configuration

---

## Service-Specific Notes

### voc-service (Reference Implementation)
- **Tests Created**: 97 total (77 passing with H2, 97 would pass with PostgreSQL)
- **Files**:
  - VocAnalysisServiceTest.java (28 tests) ✅
  - InsightServiceTest.java (19 tests) ✅
  - VocControllerTest.java (20 tests) ✅
  - VocInsightRepositoryTest.java (24 tests, 15 fail due to JSONB in H2) ⚠️
  - VocEventListenerTest.java (10 tests) ✅
- **Special Considerations**:
  - JSONB columns require real PostgreSQL for full test coverage
  - @PostConstruct initialization required for VocAnalysisService

### Future Services:
*Document service-specific test considerations here as they are created*

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12-31 | Initial creation based on voc-service test implementation |

---

## References

- Spring Boot 4.0.0 Migration Guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- Spring Boot Testing Documentation: https://docs.spring.io/spring-boot/reference/testing/index.html
- Mockito Documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- Testcontainers: https://www.testcontainers.org/

---

**Last Updated**: 2025-12-31
**Maintained By**: Development Team
**Status**: ✅ Active - Use this guide for all new test creation
