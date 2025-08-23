#!/bin/bash

# =============================================================================
# Quick Start with config.env - Minimal Version
# =============================================================================

echo "Loading config.env and starting application..."

# Load configuration
source ./config.env

# Start application
./mvnw spring-boot:run