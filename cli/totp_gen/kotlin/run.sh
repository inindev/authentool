#!/bin/sh
#
# Copyright 2025, John Clark <inindev@gmail.com>. All rights reserved.
# Licensed under the Apache License, Version 2.0. See LICENSE file in the project root for full license information.
#

set -e

SOURCE="TotpGen.kt"
JAR="TotpGen.jar"

# check if jar exists and is newer than or equal to the source file
if [ -f "$JAR" ] && [ "$SOURCE" -ot "$JAR" ] || [ "$SOURCE" -nt "$JAR" ] && [ "$SOURCE" -ot "$JAR" ]; then
    echo "jar is up-to-date, skipping compilation"
else
    echo "compiling $SOURCE..."
    kotlinc "$SOURCE" -include-runtime -d "$JAR"
    if [ $? -ne 0 ]; then
        echo "Compilation failed!"
        exit 1
    fi
    echo "Compilation successful."
fi

# run the program
echo "running $JAR..."
java -jar "$JAR"
if [ $? -ne 0 ]; then
    echo "Execution failed!"
    exit 1
fi
