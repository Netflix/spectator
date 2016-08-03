#!/bin/sh

host=$1
instanceId=$2

curl -o live_data_size.png "http://$host/api/v1/graph?q=name,jvm.gc.liveDataSize,:eq,:sum,\$name,:legend,2,:lw,name,jvm.gc.maxDataSize,:eq,:sum,\$name,:legend,2,:lw,class,MemoryPoolMXBean,:eq,id,.*Old.*,:re,:and,name,actualUsage,:eq,:and,:sum,\$id+\$name,:legend,:list,(,nf.node,$instanceId,:eq,:cq,),:each&l=0&s=e-6h&w=400&h=200&no_legend_stats=1"
