#!/bin/bash
set -e
echo "Deploying LogStream..."
docker-compose down && docker-compose build --no-cache && docker-compose up -d
sleep 10
curl -f http://localhost:8080/api-docs || echo "Health check failed!"
echo "Deployment complete! API: http://localhost:8080/swagger-ui.html"
