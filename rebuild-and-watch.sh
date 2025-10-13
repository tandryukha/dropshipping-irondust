#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔄 Rebuilding and restarting backend services...${NC}"

# Safer bash settings
set -euo pipefail

# Keep Mac awake for the duration of this script if caffeinate is available
if command -v caffeinate >/dev/null 2>&1; then
echo -e "${YELLOW}☕ Keeping Mac awake with caffeinate while this script runs...${NC}"
caffeinate -dimsu -w $$ >/dev/null 2>&1 &
fi


# Load environment from .env so docker-compose gets consistent vars in all calls
if [ -f .env ]; then
	echo -e "${YELLOW}🗂️  Loading environment from .env...${NC}"
	set -a
	. ./.env
	set +a
else
	echo -e "${YELLOW}⚠️  .env not found. Ensure OPENAI_API_KEY is exported in your shell if AI enrichment is desired.${NC}"
fi

# Quick sanity note about AI env
if [ -z "${OPENAI_API_KEY:-}" ]; then
	echo -e "${YELLOW}⚠️  OPENAI_API_KEY is empty. AI enrichment will be disabled by the app.${NC}"
fi

# Check if docker-compose is running and stop it
if docker-compose ps | grep -q "Up"; then
    echo -e "${YELLOW}📦 Stopping running docker-compose services...${NC}"
    docker-compose down
fi

# Stop and remove API service specifically, then rebuild and start with environment variables
echo -e "${YELLOW}🔨 Stopping and removing API service...${NC}"
docker-compose stop api && docker-compose rm -f api

echo -e "${YELLOW}🔨 Rebuilding and starting API service with environment variables...${NC}"
AI_ENRICH="${AI_ENRICH:-true}" docker-compose up -d --build --force-recreate api | cat

# Start other services if they exist (avoid recreating API with different env)
echo -e "${YELLOW}🚀 Starting other services...${NC}"
docker-compose up -d --no-recreate

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

# Documentation is available as .md files in docs/ directory
echo -e "${BLUE}📖 Documentation: docs/ directory (simple .md files)${NC}"

# Follow only new logs for all services (omit history)
docker-compose logs -f --tail=0
