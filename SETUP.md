# Quick Setup Guide

## Prerequisites

1. Java 17 or higher installed
2. Maven 3.6+ installed
3. Bullhorn API credentials (when available from employer)

## Initial Setup

### 1. Clone and Build

```bash
cd Bullhorn
mvn clean install
```

### 2. Configure Credentials

Create a `.env` file 

```unix
touch .env.example .env
```

Edit `.env` and add your Bullhorn credentials:

```bash
BULLHORN_CLIENT_ID=your-actual-client-id
BULLHORN_CLIENT_SECRET=your-actual-secret
BULLHORN_REFRESH_TOKEN=your-initial-token
```

### 3. Run the Application

#### Using Maven (Development)

```bash
# Load environment variables
export $(cat .env | xargs)

# Run application
mvn spring-boot:run
```

## Testing the API

### 1. Health Check

```bash
curl http://localhost:8080/api/v1/timesheets/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "Bullhorn Timesheet Service"
}
```

### 2. Query Timesheets (POST)

```bash
curl -X POST http://localhost:8080/api/v1/timesheets/query \
  -H "Content-Type: application/json" \
  -d '{
    "candidateIds": [101, 102, 103]
  }'
```

### 3. Query Timesheets (GET)

```bash
curl "http://localhost:8080/api/v1/timesheets?candidateIds=101,102,103"
```


## Troubleshooting

### Application fails to start
- Check Java version: `java -version` (must be 17+)
- Verify Maven build: `mvn clean install`
- Check logs in console output

### "No refresh token available"
- Ensure `.env` file exists and is loaded
- Verify environment variables: `echo $BULLHORN_REFRESH_TOKEN`
- Check that value is not "PLACEHOLDER"

### API calls return 401
- Credentials may be invalid or expired
- Check Bullhorn API documentation for credential format
- Review application logs for detailed error messages

## Project Structure Overview

```
Bullhorn/
├── src/main/java/com/example/bullhorn/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST endpoints
│   ├── dto/                 # Data transfer objects
│   ├── repository/          # Token storage abstraction
│   └── service/             # Business logic
├── src/main/resources/
│   ├── application.yml      # Base configuration
│   ├── application-dev.yml  # Dev profile config
│   └── application-prod.yml # Production config
├── pom.xml                  # Maven dependencies
└── README.md                # Full documentation
```

## Support

For issues or questions, refer to:
- `README.md` - Comprehensive documentation
- Application logs - Check console output for errors
- Bullhorn API documentation - https://bullhorn.github.io/rest-api-docs/
