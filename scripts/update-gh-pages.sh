#!/bin/bash

#
# Update gh-pages branch with reports
#

VERSION=${1:-latest}
make -f scripts/Makefile -e VERSION=${VERSION} clean rebuild/$VERSION

