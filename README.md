# Bullhorn Timesheet Service

A production-ready Spring Boot 3 REST API microservice for querying Bullhorn timesheets by candidate IDs.

## Features

- **OAuth Token Management**: Handles Bullhorn's refresh token rotation protocol
- **Session Caching**: Caches BhRestToken for 50-55 minutes to minimize API calls
- **Automatic Re-authentication**: Handles 401 errors by clearing session and re-authenticating
- **Thread-Safe Token Storage**: Uses AtomicReference with pluggable persistence (in-memory, file, or custom)
- **Clean REST API**: Provides simple endpoints for querying timesheets by candidate IDs

## Architecture

### Authentication Flow

1. **OAuth Token Exchange**:
   - POST to `https://auth.bullhornstaffing.com/oauth/token`
   - Exchanges `refresh_token` for `access_token`
   - **CRITICAL**: Bullhorn invalidates the old `refresh_token` and returns a NEW one
   - The service captures and persists the new token immediately

2. **Session Login**:
   - GET to `https://rest.bullhornstaffing.com/rest-services/login`
   - Uses `access_token` to obtain `BhRestToken` and cluster `restUrl`
   - Session is cached for ~50 minutes

3. **API Queries**:
   - Uses cached `BhRestToken` and `restUrl` for timesheet queries
   - Automatically re-authenticates on 401 errors

### Project Structure

```
src/main/java/com/example/bullhorn/
├── BullhornTimesheetApplication.java       # Main application
├── config/
│   ├── BullhornProperties.java             # Configuration properties
│   └── RestClientConfig.java               # RestClient bean configuration
├── controller/
│   └── TimesheetController.java            # REST endpoints
├── dto/
│   ├── BullhornDto.java                    # Bullhorn API response DTOs
│   └── TimesheetResponseDto.java           # Clean domain DTOs
├── repository/
│   └── RefreshTokenStore.java              # Token persistence abstraction
└── service/
    ├── BullhornAuthService.java            # OAuth & session management
    └── BullhornTimesheetService.java       # Timesheet queries
```

## Configuration

### Environment Variables

Set these before running the application:

```bash
export BULLHORN_CLIENT_ID=your-client-id
export BULLHORN_CLIENT_SECRET=your-client-secret
export BULLHORN_REFRESH_TOKEN=your-initial-refresh-token
```

### Application Profiles

- **dev** (default): Uses `InMemoryRefreshTokenStore` (tokens lost on restart)
- **file-storage**: Uses `FileRefreshTokenStore` (persists to `bullhorn-refresh-token.txt`)
- **prod**: Placeholder for database/vault implementation

To use file storage:

```bash
java -jar -Dspring.profiles.active=file-storage target/bullhorn-timesheet-service-1.0.0-SNAPSHOT.jar
```

### Configuration Properties

See `src/main/resources/application.yml` for all available properties.

## Building & Running

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Build

```bash
mvn clean package
```

### Run

```bash
# Development mode (in-memory token storage)
mvn spring-boot:run

# Or with environment variables
BULLHORN_CLIENT_ID=xxx BULLHORN_CLIENT_SECRET=yyy BULLHORN_REFRESH_TOKEN=zzz mvn spring-boot:run
```

### Run JAR

```bash
java -jar target/bullhorn-timesheet-service-1.0.0-SNAPSHOT.jar
```

## API Endpoints

### Query Timesheets (POST)

```bash
POST http://localhost:8080/api/v1/timesheets/query
Content-Type: application/json

{
  "candidateIds": [101, 102, 103]
}
```

### Query Timesheets (GET)

```bash
GET http://localhost:8080/api/v1/timesheets?candidateIds=101,102,103
```

### Response Example

```json
{
  "timesheets": [
    {
      "timesheetId": 12345,
      "status": "Approved",
      "dateAdded": "2024-01-15T10:30:00Z",
      "comments": "Week ending 1/14/2024",
      "hours": 40.0,
      "placement": {
        "placementId": 5678,
        "candidate": {
          "candidateId": 101,
          "firstName": "John",
          "lastName": "Doe",
          "fullName": "John Doe"
        },
        "jobOrder": {
          "jobOrderId": 9999,
          "title": "Software Engineer"
        }
      }
    }
  ],
  "totalCount": 1,
  "requestedCandidateIds": [101, 102, 103]
}
```

### Health Check

```bash
GET http://localhost:8080/api/v1/timesheets/health
```

## Refresh Token Rotation

**CRITICAL**: Bullhorn invalidates refresh tokens on single use. The service handles this by:

1. Capturing the new `refresh_token` from every OAuth response
2. Persisting it immediately via `RefreshTokenStore`
3. Using the new token for subsequent authentication

### Token Storage Implementations

#### InMemoryRefreshTokenStore (dev profile)
- Tokens stored in `AtomicReference`
- Tokens lost on application restart
- Suitable for development/testing

#### FileRefreshTokenStore (file-storage profile)
- Tokens persisted to `bullhorn-refresh-token.txt`
- Survives application restarts
- Suitable for simple deployments

#### ProductionRefreshTokenStore (prod profile)
- **TODO**: Implement database or vault storage
- Examples:
  - Spring Data JPA: Store in encrypted database column
  - HashiCorp Vault: Use `VaultTemplate.write()`
  - AWS Secrets Manager: Use AWS SDK

## Production Deployment

### Security Checklist

- [ ] Replace placeholder credentials with actual Bullhorn API credentials
- [ ] Implement production-grade `RefreshTokenStore` (database/vault)
- [ ] Enable HTTPS/TLS for all endpoints
- [ ] Add authentication/authorization to REST endpoints
- [ ] Configure proper logging (centralized, structured)
- [ ] Set up monitoring and alerting
- [ ] Add circuit breakers for Bullhorn API calls
- [ ] Implement rate limiting

### Kubernetes Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bullhorn-timesheet-service
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: app
          image: bullhorn-timesheet-service:1.0.0
          env:
            - name: BULLHORN_CLIENT_ID
              valueFrom:
                secretKeyRef:
                  name: bullhorn-credentials
                  key: client-id
            - name: BULLHORN_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: bullhorn-credentials
                  key: client-secret
            - name: BULLHORN_REFRESH_TOKEN
              valueFrom:
                secretKeyRef:
                  name: bullhorn-credentials
                  key: refresh-token
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
```

## Testing Without Production Credentials

Since no sandbox environment is available:

1. **Mock Responses**: Create test profiles with mocked Bullhorn responses
2. **Manual Testing**: Use Postman/curl with actual credentials when received
3. **Integration Tests**: Use WireMock to simulate Bullhorn API

Example test profile (`application-mock.yml`):

```yaml
# Future enhancement: Create mock implementations for testing
```

## Troubleshooting

### "No refresh token available"
- Ensure `BULLHORN_REFRESH_TOKEN` environment variable is set
- Check that token is not "PLACEHOLDER"

### "401 Unauthorized" persists
- Verify OAuth credentials are correct
- Check if refresh token has been manually revoked
- Review logs for authentication errors

### Session expires too quickly
- Adjust `bullhorn.session.cache-duration` in `application.yml`
- Default is 50 minutes (Bullhorn sessions last ~60 minutes)

## License

Proprietary - Internal Use Only
