#!/bin/sh
# Builds the mod for every Minecraft version in versions/ (one jar each, all in build/libs/).
# A failed version doesn't stop the others; the failed ones are listed at the end.
# Extra arguments go to every Gradle run, e.g.: ./build-all.sh --no-configuration-cache
cd "$(dirname "$0")"
failed=""
for f in versions/*.properties; do
    mc=$(basename "$f" .properties)
    printf "\n===== Minecraft %s =====\n" "$mc"
    ./gradlew build -Pmc="$mc" "$@" || { echo "Build for $mc failed."; failed="$failed $mc"; }
done
echo
if [ -n "$failed" ]; then
    echo "Failed versions:$failed"
    echo "Jars of the other versions: build/libs"
    exit 1
fi
echo "All versions built: build/libs"
