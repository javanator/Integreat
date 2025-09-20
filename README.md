# Integreat

## Overview

This application provides a project skeleton for a Java Spring Boot
backend with a React frontend. It supports authentication and authorization
using JWT tokens and OAuth2. It also is packaged with a SQLite database and docker container build. 
It is currently wired up to Intuit's QuickBooks API and uses Intuit's OAuth2
authorization flow. Other OAuth2 providers can be easily added. I have also added
a Docker container build to make production deployment easier.

```
Integreat/
├── src/main/java/             # Main Java source code
│   ├── com.integreat          # Application package
│   │   ├── client/            # Connectivity for other services
│   │   ├── config/            # Configuration classes
│   │   ├── controller/        # REST controllers
│   │   ├── model/             # Data models and entities
│   │   └── Application.java   # Main application class
├── src/main/resources/        # Application resources
│   ├── static/                # Static assets (CSS, JS, images)
│   ├── templates/             # Template files (Thymeleaf)
│   ├── application.properties # Application configuration
├── frontend/                  # React frontend source code
├── build.gradle               # Gradle build configuration
└── Dockerfile                 # Docker container configuration
```

## Getting Started
- Create an Intuit developer account at https://developer.intuit.com/
- Create a new app at https://developer.intuit.com/app/dashboard
- Fill in the Intuit oauth keys in the application properties file
- 

## Before you go to prod ...
- Check the security notes below.
- Frontend resource caching is turned off by default. This is set in the WebConfig class.
- There are two options for serving the frontend. You can either
  1. Serve from the SpringBoot backend by separately launching the "watchReact" gradle build task ```./gradlew watchReact```
     - The server will be available at http://localhost:8080/
  1. Serve the frontend in its own server instance 
     - from ./frontend/ launch ```npm start```.
     - The frontend will be available at http://localhost:3000/ while the backend application runs concurrently on http://localhost:8080/
- It will be necessary to use a shared instance of a database other than SQLite for production if you have multiple hosts.

## Security Notes
- I have not added HTTPS
- I have not added CSRF protection
- I have not added token validation or expiration checks. 
- SessionIDs are sequentially generated
- I have not added any log rotation or purging of old logs.

## Future
- Add HTTPS
- Add CSRF protection
- Add token validation and expiration checks
- Add log rotation and purging
- Add other OAuth2 providers


