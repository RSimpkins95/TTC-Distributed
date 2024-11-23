#!/bin/bash

echo "Running Test Case 1: No Cycles"
docker-compose -f docker-compose.yml up --build --abort-on-container-exit
docker-compose -f docker-compose.yml down

sleep 5

echo "Running Test Case 1: With Cycles"
docker-compose -f docker-compose-with-cycles.yml up --build --abort-on-container-exit
docker-compose -f docker-compose-with-cycles.yml down