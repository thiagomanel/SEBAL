#!/bin/bash

R_ALGORITHM_PATH=$1
R_EXEC_DIR=$2
TMP_DIR_PATH=$3

TIMEOUT=12600

NUMBER_OF_TIMEOUTS=0

echo "Executing R script..."
timeout $TIMEOUT Rscript $R_ALGORITHM_PATH $R_EXEC_DIR $TMP_DIR_PATH
PROCESS_OUTPUT=$?

echo "RScript_process_output=$PROCESS_OUTPUT"
if [ $PROCESS_OUTPUT -eq 124 ]
then
  NUMBER_OF_TIMEOUTS=$(($NUMBER_OF_TIMEOUTS+1))
  echo "NUMBER OF TIMEOUTS $NUMBER_OF_TIMEOUTS"
  exit 124
elif [ $PROCESS_OUTPUT -ne 0 ]
then
  exit 1
else
  exit 0
fi
