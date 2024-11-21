#!/bin/bash

echo "Running Test Case 1: No Cycles"
docker-compose -f docker-compose-no-cycles.yml up --build --abort-on-container-exit
docker-compose -f docker-compose-no-cycles.yml down

sleep 5

echo "Running Test Case 2: With Cycles"
docker-compose -f docker-compose-with-cycles.yml up --build --abort-on-container-exit
docker-compose -f docker-compose-with-cycles.yml down
