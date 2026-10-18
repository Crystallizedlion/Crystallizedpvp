#!/usr/bin/env bash
# crystallizedsmp launcher - Paper 26.3 needs Java 25 or newer.
# Put paper.jar (Paper 26.3) next to this file.
java -version 2>&1 | head -1
java -Xms4G -Xmx4G \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -jar paper.jar --nogui
