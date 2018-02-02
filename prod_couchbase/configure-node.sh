#!/bin/bash

set -x
set -m

/entrypoint.sh couchbase-server &

echo "Type: $TYPE"

sleep 60

# Setup index and memory quota
curl -v -X POST http://127.0.0.1:8091/pools/default -d memoryQuota=2048 -d indexMemoryQuota=4096

# Setup services
curl -v -X POST http://127.0.0.1:8091/node/controller/setupServices -d services=kv%2Cn1ql%2Cindex

# Setup credentials
curl -v http://127.0.0.1:8091/settings/web -d port=8091 -d username=Administrator -d password=b2c1509

# Setup Memory Optimized Indexes (commented since is only allowed for the enterprise edition)
curl -i -u Administrator:b2c1509 -X POST http://127.0.0.1:8091/settings/indexes -d 'storageMode=forestdb'

if [ "$TYPE" = "WORKER" ]; then
  echo "Sleeping ..."
  sleep 60

  #IP=`hostname -s`
  IP=`hostname -I | cut -d ' ' -f1`
  echo "IP: " $IP
  couchbase-cli server-add --cluster=$COUCHBASE_MASTER --user=Administrator --password=b2c1509 --server-add=$IP --server-add-username=Administrator --server-add-password=b2c1509
  couchbase-cli rebalance --cluster=$COUCHBASE_MASTER --user=Administrator --password=b2c1509
fi;

fg 1