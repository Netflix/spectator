#!/bin/bash

#
# Update gh-pages branch with reports
#

# Keeping separate from main build dir so clean target of gradle can be used
BUILD_DIR=gh-pages-build

function cleanBuild {
  rm -rf $BUILD_DIR
}

function cloneGhPages {
  mkdir -p $BUILD_DIR
  cd $BUILD_DIR
  rm -rf gh-pages
  git clone git@github.com:Netflix/spectator.git gh-pages
  cd gh-pages
  git checkout gh-pages
  cd ../../
}

function updateForVersion {
  VERSION=${1:-HEAD}
  git checkout master
  if [ "$VERSION" != "HEAD" ]; then
    git checkout tags/$VERSION
  fi
  ./gradlew clean jacocoTestReport build buildDashboard
  
  # Update the reports: code-coverage, findbugs, etc
  REPORT_DIR=$BUILD_DIR/gh-pages/reports/$VERSION
  find . -type d -name reports | grep '^./spectator-' | while read dir; do
    projectName=$(echo $dir | gsed -r 's#^\./##;s#/.*##')
    projectDir=$REPORT_DIR/$projectName
    rm -rf $projectDir
    mkdir -p $projectDir
    cp -rf $dir/* $projectDir/
  done
  
  JAVADOC_DIR=$BUILD_DIR/gh-pages/javadoc/$VERSION
  find . -type d -name javadoc | grep '^./spectator-' | grep '/docs/' | while read dir; do
    projectName=$(echo $dir | gsed -r 's#^\./##;s#/.*##')
    projectDir=$JAVADOC_DIR/$projectName
    rm -rf $projectDir
    mkdir -p $projectDir
    cp -rf $dir/* $projectDir/
  done
}

function updateIfMissing {
  VERSION=$1
  REPORT_DIR=$BUILD_DIR/gh-pages/reports/$VERSION
  if [ ! -d "$REPORT_DIR" ]; then
    updateForVersion $VERSION
  fi
}

function updateAllVersions {
  git tag -l | while read tag; do
    updateIfMissing $tag
  done
  updateForVersion HEAD
}

cleanBuild
cloneGhPages
updateAllVersions
