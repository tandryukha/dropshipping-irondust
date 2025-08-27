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

# Start MkDocs dev server in background and open in browser
start_docs_server() {
	DOCS_URL="http://127.0.0.1:8000"

	# Helper: check if port is already in use
	port_in_use=false
	if command -v nc >/dev/null 2>&1; then
		if nc -z 127.0.0.1 8000 >/dev/null 2>&1; then
			port_in_use=true
		fi
	elif command -v lsof >/dev/null 2>&1; then
		if lsof -i :8000 -sTCP:LISTEN >/dev/null 2>&1; then
			port_in_use=true
		fi
	fi

	if [ "$port_in_use" = true ]; then
		echo -e "${YELLOW}📚 Docs server appears to be running at ${DOCS_URL}${NC}"
	else
		echo -e "${YELLOW}📚 Starting docs server (MkDocs) on ${DOCS_URL}...${NC}"
		# Ensure mkdocs is available; install into a venv if needed
		if ! command -v mkdocs >/dev/null 2>&1; then
			if [ ! -d .venv ]; then
				python3 -m venv .venv || true
			fi
			# shellcheck disable=SC1091
			. ./.venv/bin/activate || true
			python3 -m pip install --upgrade pip >/dev/null 2>&1 || true
			if [ -f docs/requirements.txt ]; then
				python3 -m pip install -r docs/requirements.txt >/dev/null 2>&1 || true
			else
				python3 -m pip install mkdocs mkdocs-material >/dev/null 2>&1 || true
			fi
		fi
		# Run MkDocs in background; avoid killing on shell exit by using nohup
		(nohup mkdocs serve -a 127.0.0.1:8000 >/dev/null 2>&1 &) || true
	fi

	# Try to open in default browser
	if command -v open >/dev/null 2>&1; then
		open "$DOCS_URL" >/dev/null 2>&1 || true
	elif command -v xdg-open >/dev/null 2>&1; then
		xdg-open "$DOCS_URL" >/dev/null 2>&1 || true
	fi

	echo -e "${BLUE}📖 Docs: ${DOCS_URL}${NC}"
}

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

# Start documentation server and open in browser (non-blocking)
start_docs_server || true

# Follow only new logs for all services (omit history)
docker-compose logs -f --tail=0
