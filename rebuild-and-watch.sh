#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔄 Rebuilding and restarting backend services...${NC}"

# Stop all services
echo -e "${YELLOW}📦 Stopping all services...${NC}"
docker-compose down

# Rebuild the API service
echo -e "${YELLOW}🔨 Rebuilding API service...${NC}"
docker-compose build --no-cache api

# Start all services
echo -e "${YELLOW}🚀 Starting all services...${NC}"
docker-compose up -d

# Wait a moment for services to fully start
echo -e "${YELLOW}⏳ Waiting for services to start...${NC}"
sleep 3

# Check if services are running
echo -e "${YELLOW}🔍 Checking service status...${NC}"
docker-compose ps

echo -e "${GREEN}✅ Services rebuilt and restarted successfully!${NC}"
echo -e "${BLUE}📋 Following logs for all services (Ctrl+C to stop)...${NC}"
echo -e "${BLUE}   API: http://localhost:4000${NC}"
echo -e "${BLUE}   MeiliSearch: http://localhost:7700${NC}"
echo ""

# Follow logs for all services
docker-compose logs -f
