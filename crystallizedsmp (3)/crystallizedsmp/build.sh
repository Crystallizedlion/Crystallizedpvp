#!/usr/bin/env bash
# Builds CrystallizedPvP.jar and copies it into server/plugins. Needs JDK 25 + Maven.
set -e
cd "$(dirname "$0")/plugin"
mvn -q clean package
cp target/CrystallizedPvP.jar ../server/plugins/CrystallizedPvP.jar
echo "Built -> server/plugins/CrystallizedPvP.jar"
