#!/bin/sh
# Rebuilds CustomPlugin-Pack.zip from the CustomPlugin-Pack folder and prints its SHA-1.
cd "$(dirname "$0")/CustomPlugin-Pack" || exit 1
rm -f ../CustomPlugin-Pack.zip
zip -r -X ../CustomPlugin-Pack.zip pack.mcmeta pack.png assets
sha1sum ../CustomPlugin-Pack.zip
