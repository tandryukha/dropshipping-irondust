#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔄 Rebuilding and restarting backend services...${NC}"

# Check if docker-compose is running and stop it
if docker-compose ps | grep -q "Up"; then
    echo -e "${YELLOW}📦 Stopping running docker-compose services...${NC}"
    docker-compose down
fi

# Stop and remove API service specifically, then rebuild and start with environment variables
echo -e "${YELLOW}🔨 Stopping and removing API service...${NC}"
docker-compose stop api && docker-compose rm -f api

echo -e "${YELLOW}🔨 Rebuilding and starting API service with environment variables...${NC}"
OPENAI_API_KEY=$(grep -m1 '^OPENAI_API_KEY=' .env | cut -d'=' -f2-) AI_ENRICH=true docker-compose up -d --build api | cat

# Start other services if they exist
echo -e "${YELLOW}🚀 Starting other services...${NC}"
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
