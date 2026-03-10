# LogStream Frontend Implementation Guide

## Overview
This guide provides step-by-step instructions for implementing a server-side rendered HTML dashboard for the LogStream system using Spring Boot and Thymeleaf.

## Technology Stack
- **Backend**: Spring Boot 3.2.0
- **Template Engine**: Thymeleaf
- **Styling**: Tailwind CSS (via CDN)
- **Charts**: Chart.js (via CDN)
- **Icons**: Font Awesome (via CDN)

## Step 1: Add Dependencies

### Update pom.xml
Add the following dependencies to your `pom.xml` file:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

The project should already have:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`

## Step 2: Create Web Controller

Create `WebController.java` in `src/main/java/com/logstream/controller/`:

### Key Features:
- **Dashboard**: Overview with analytics and statistics
- **Retention Policies**: CRUD operations for retention policies
- **Log Management**: View, filter, and export logs
- **Analytics**: Detailed analytics with charts
- **CSV Export/Import**: Bulk log data operations

### Controller Methods:
```java
@Controller
public class WebController {
    
    // Dashboard - GET /
    @GetMapping("/")
    public String dashboard(Model model)
    
    // Retention Policies - GET /retention
    @GetMapping("/retention")
    public String retentionPolicies(Model model)
    
    // Add Policy - POST /retention/add
    @PostMapping("/retention/add")
    public String addRetentionPolicy(@ModelAttribute RetentionPolicy policy, RedirectAttributes redirectAttributes)
    
    // Edit Policy - GET /retention/edit/{id}
    @GetMapping("/retention/edit/{id}")
    public String editRetentionPolicy(@PathVariable Long id, Model model)
    
    // Update Policy - POST /retention/update/{id}
    @PostMapping("/retention/update/{id}")
    public String updateRetentionPolicy(@PathVariable Long id, @ModelAttribute RetentionPolicy policy, RedirectAttributes redirectAttributes)
    
    // Delete Policy - GET /retention/delete/{id}
    @GetMapping("/retention/delete/{id}")
    public String deleteRetentionPolicy(@PathVariable Long id, RedirectAttributes redirectAttributes)
    
    // Log Management - GET /logs
    @GetMapping("/logs")
    public String logManagement(@RequestParam(defaultValue = "0") int page, ...)
    
    // CSV Export - GET /logs/export/csv
    @GetMapping("/logs/export/csv")
    public String exportLogsToCsv(@RequestParam(required = false) String service, ...)
    
    // CSV Import Form - GET /logs/import
    @GetMapping("/logs/import")
    public String importLogsForm()
    
    // CSV Import - POST /logs/import/csv
    @PostMapping("/logs/import/csv")
    public String importLogsFromCsv(@RequestParam("file") MultipartFile file, ...)
    
    // Analytics - GET /analytics
    @GetMapping("/analytics")
    public String analytics(@RequestParam(required = false) String service, ...)
}
```

## Step 3: Create Thymeleaf Templates

### Directory Structure
```
src/main/resources/templates/
├── layout.html              # Base layout template
├── dashboard.html           # Dashboard page
├── retention.html           # Retention policies management
├── edit-retention.html      # Edit retention policy form
├── logs.html               # Log management page
├── analytics.html          # Analytics page
├── import-logs.html        # CSV import form
└── csv-export.html         # CSV export template
```

### Base Layout (`layout.html`)
- Responsive sidebar navigation
- Mobile-friendly with hamburger menu
- Flash message support
- Common header with system status
- CDN integration for Tailwind CSS, Chart.js, and Font Awesome

### Key Template Features:

#### 1. Dashboard (`dashboard.html`)
- Statistics cards (total logs, error count, service count)
- Error rate by service chart
- Recent log entries table
- Quick action buttons

#### 2. Retention Policies (`retention.html`)
- Policy table with CRUD actions
- Add new policy form
- Edit and delete functionality
- Service name validation

#### 3. Log Management (`logs.html`)
- Paginated log entries table
- Service and level filtering
- Search functionality
- Export to CSV button
- Import CSV button

#### 4. Analytics (`analytics.html`)
- Interactive charts using Chart.js
- Log volume time series
- Error rate trends
- Service comparison
- Granularity selection (hour/day)

#### 5. CSV Operations
- **Export**: Filtered logs exported as CSV
- **Import**: CSV file upload with validation
- **Bulk Operations**: Handle large datasets efficiently

## Step 4: Template Implementation Details

### Dashboard Template Structure:
```html
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="layout :: head">
    <title>Dashboard - LogStream</title>
</head>
<body>
    <div th:replace="layout :: sidebar"></div>
    <div th:replace="layout :: header"></div>
    
    <main>
        <!-- Statistics Cards -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div class="bg-white p-6 rounded-lg shadow">
                <h3 class="text-sm font-medium text-gray-500">Total Logs</h3>
                <p class="text-2xl font-bold text-gray-900" th:text="${totalLogs}">0</p>
            </div>
            <!-- More cards... -->
        </div>
        
        <!-- Charts Section -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div class="bg-white p-6 rounded-lg shadow">
                <h3 class="text-lg font-semibold mb-4">Error Rate by Service</h3>
                <canvas id="errorRateChart"></canvas>
            </div>
            <!-- More charts... -->
        </div>
    </main>
</body>
</html>
```

### Retention Policy Form:
```html
<form th:action="@{/retention/add}" method="post" th:object="${newPolicy}" class="space-y-4">
    <div>
        <label class="block text-sm font-medium text-gray-700">Service Name</label>
        <input type="text" th:field="*{serviceName}" required 
               class="mt-1 block w-full rounded-md border-gray-300 shadow-sm">
    </div>
    
    <div>
        <label class="block text-sm font-medium text-gray-700">Retention Days</label>
        <input type="number" th:field="*{retentionDays}" min="1" required
               class="mt-1 block w-full rounded-md border-gray-300 shadow-sm">
    </div>
    
    <div>
        <label class="flex items-center">
            <input type="checkbox" th:field="*{archiveEnabled}" 
                   class="rounded border-gray-300 text-blue-600">
            <span class="ml-2 text-sm text-gray-700">Enable Archive</span>
        </label>
    </div>
    
    <button type="submit" class="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700">
        Add Policy
    </button>
</form>
```

## Step 5: Security Configuration

### Update Security Config
Add web endpoint access to your `SecurityConfig`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/", "/dashboard", "/analytics", "/logs").authenticated()
                .requestMatchers("/retention/**").hasRole("ADMIN")
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login")
                .permitAll()
            );
        
        return http.build();
    }
}
```

## Step 6: CSV Processing

### CSV Export Template (`csv-export.html`)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Log Export</title>
</head>
<body>
    <th:block th:fragment="csv-content">
        ID,Timestamp,Service,Level,Message,Source,Created At
        <th:block th:each="log : ${logs}">
            <span th:text="${log.id}"></span>,
            <span th:text="${#temporals.format(log.timestamp, 'yyyy-MM-dd HH:mm:ss')}"></span>,
            <span th:text="${log.serviceName}"></span>,
            <span th:text="${log.level}"></span>,
            <span th:text="${#strings.escapeXml(log.message)}"></span>,
            <span th:text="${log.source}"></span>,
            <span th:text="${#temporals.format(log.createdAt, 'yyyy-MM-dd HH:mm:ss')}"></span>
        </th:block>
    </th:block>
</body>
</html>
```

### CSV Import Service
```java
@Service
public class CsvImportService {
    
    public void importLogsFromCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean skipHeader = true;
            
            while ((line = reader.readLine()) != null) {
                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }
                
                String[] fields = line.split(",");
                LogEntry logEntry = LogEntry.builder()
                    .id(UUID.fromString(fields[0]))
                    .timestamp(Instant.parse(fields[1]))
                    .serviceName(fields[2])
                    .level(LogLevel.valueOf(fields[3]))
                    .message(fields[4])
                    .source(fields.length > 5 ? fields[5] : null)
                    .createdAt(Instant.now())
                    .build();
                
                logEntryRepository.save(logEntry);
            }
        }
    }
}
```

## Step 7: Chart Integration

### JavaScript for Dashboard Charts
```javascript
// Error Rate Chart
const errorRateCtx = document.getElementById('errorRateChart').getContext('2d');
const errorRateChart = new Chart(errorRateCtx, {
    type: 'bar',
    data: {
        labels: /* Thymeleaf will inject service names */,
        datasets: [{
            label: 'Error Rate (%)',
            data: /* Thymeleaf will inject error rates */,
            backgroundColor: 'rgba(239, 68, 68, 0.5)',
            borderColor: 'rgba(239, 68, 68, 1)',
            borderWidth: 1
        }]
    },
    options: {
        responsive: true,
        scales: {
            y: {
                beginAtZero: true,
                max: 100
            }
        }
    }
});

// Log Volume Time Series
const volumeCtx = document.getElementById('volumeChart').getContext('2d');
const volumeChart = new Chart(volumeCtx, {
    type: 'line',
    data: {
        labels: /* Thymeleaf will inject timestamps */,
        datasets: [{
            label: 'Log Volume',
            data: /* Thymeleaf will inject volumes */,
            borderColor: 'rgba(59, 130, 246, 1)',
            backgroundColor: 'rgba(59, 130, 246, 0.1)',
            tension: 0.4
        }]
    },
    options: {
        responsive: true,
        scales: {
            y: {
                beginAtZero: true
            }
        }
    }
});
```

## Step 8: Application Properties

### Update application.properties
```properties
# Thymeleaf Configuration
spring.thymeleaf.cache=false
spring.thymeleaf.mode=HTML
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html

# MVC Configuration
spring.mvc.view.prefix=/templates/
spring.mvc.view.suffix=.html

# File Upload Configuration
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Security Configuration
spring.security.user.name=admin
spring.security.user.password=password123
```

## Step 9: Testing

### Manual Testing Checklist:
1. **Dashboard**: Verify statistics and charts load correctly
2. **Navigation**: Test sidebar and mobile responsiveness
3. **Retention Policies**: Test CRUD operations
4. **Log Management**: Test filtering, pagination, and export
5. **CSV Import**: Test file upload and processing
6. **Analytics**: Test charts and filters
7. **Security**: Verify role-based access control

### Automated Testing:
```java
@WebMvcTest(WebController.class)
class WebControllerTest {
    
    @Test
    void shouldReturnDashboardPage() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("totalLogs", "errorCount"));
    }
    
    @Test
    void shouldReturnRetentionPoliciesPage() {
        mockMvc.perform(get("/retention"))
            .andExpect(status().isOk())
            .andExpect(view().name("retention"));
    }
}
```

## Step 10: Deployment Considerations

### Production Configuration:
1. **Enable Thymeleaf Caching**: `spring.thymeleaf.cache=true`
2. **Static Resources**: Configure CDN for CSS/JS
3. **Database**: Optimize queries for dashboard performance
4. **Security**: Enable CSRF protection for forms
5. **Monitoring**: Add health check endpoints

### Performance Optimization:
1. **Database Indexing**: Ensure proper indexes for dashboard queries
2. **Caching**: Cache analytics data where appropriate
3. **Pagination**: Implement efficient pagination for large datasets
4. **Compression**: Enable response compression

## Troubleshooting

### Common Issues:
1. **Template Not Found**: Check template path and file extension
2. **CSS Not Loading**: Verify static resource configuration
3. **Form Submission Errors**: Check CSRF token configuration
4. **Chart Not Rendering**: Verify Chart.js CDN and data format
5. **CSV Export Issues**: Check content-type headers and encoding

### Debug Tips:
1. Enable Thymeleaf debug logging: `logging.level.org.thymeleaf=DEBUG`
2. Check browser developer tools for JavaScript errors
3. Verify network requests and responses
4. Test with different browsers for compatibility

## Next Steps

1. **Enhanced Analytics**: Add more sophisticated chart types
2. **Real-time Updates**: Implement WebSocket for live log streaming
3. **Advanced Filtering**: Add date range and regex search
4. **User Management**: Add user profile and preference management
5. **API Integration**: Add REST API for mobile clients

This implementation provides a solid foundation for a production-ready log management dashboard with server-side rendering, ensuring good performance and security.
