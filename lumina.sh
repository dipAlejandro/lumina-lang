#!/bin/bash

src=$1

if [ ! -e $src ]; then
	echo "Error: Se debe proporcionar codigo fuente '.lum'"
fi

java -jar interpreter.jar $src
