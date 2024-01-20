#!/bin/sh

set -e

MAVEN_PROJECT=$(/bin/cat < settings.gradle.kts | grep 'rootProject.name =' | cut -d= -f2 | awk '{ print substr( $0, 3, length($0)-3 ) }')
MAVEN_GROUP=$(/bin/cat < build.gradle.kts | grep 'group =' | cut -d= -f2 | awk '{ print substr( $0, 3, length($0)-3 ) }')
MAVEN_VERSION=$(/bin/cat < build.gradle.kts | grep 'version =' | cut -d= -f2 | awk '{ print substr( $0, 3, length($0)-3 ) }')
MAVEN_PATH=$(echo "$MAVEN_GROUP" | tr . / | awk '{print $1"/'"$MAVEN_PROJECT"'"}'"$1")

./gradlew publishToMavenLocal

mkdir -p .gradle/bundle && cd .gradle/bundle || exit

cp -R ~/.m2/repository/"$MAVEN_PATH"/"$MAVEN_VERSION"/* ./

rm -rf ./*.module*
for file in ./*; do md5sum "$file" | awk '{print $1}' > "$file".md5; done
for file in ./*; do sha1sum  "$file" | awk '{print $1}' > "$file".sha1; done
rm -rf ./*.md5.sha1

mkdir -p "$MAVEN_PATH"/"$MAVEN_VERSION"
cp -R ./actor4k-"$MAVEN_VERSION"* ./"$MAVEN_PATH"/"$MAVEN_VERSION"

rm -rf ./actor4k*
rm -rf bundle-"$MAVEN_VERSION".zip
zip -r bundle-"$MAVEN_VERSION".zip io

rm -rf io
rm -rf coordinates-"$MAVEN_VERSION".txt
echo "$MAVEN_GROUP":"$MAVEN_PROJECT":"$MAVEN_VERSION" > coordinates-"$MAVEN_VERSION".txt

rm -rf ./bundle*.md5* && rm -rf ./bundle*.sha1*
rm -rf ./coordinates*.md5* && rm -rf ./coordinates*.sha1*
