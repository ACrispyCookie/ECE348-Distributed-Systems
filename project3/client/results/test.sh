#!/bin/bash
echo "BlockSize,CacheBlocks,FreshT,Time,MBps,PktsSent,PktsRecv,BytesSent,BytesRecv" > results.csv

# 1. Test Baseline (No Cache) vs Block Sizes
# for blk in 1024 4096 8192; do
#     ./benchmark 10.0.0.2 5000 $blk 0 5 /test_10mb.dat >> results.csv
# done

# 2. Test Cache Sizes (Fixed 4096 Block, Fixed 5s Fresh)
for cache in 1000 2000 2250 2500 2750 3000; do
    ./benchmark 10.0.0.2 5000 4096 $cache 5 /test_10mb.dat >> results.csv
done

# 3. Test Freshness (Fixed 4096 Block, 1000 Cache)
# for fresh in 1 5 10 60; do
#     ./benchmark 10.0.0.2 5000 4096 1000 $fresh /test_10mb.dat >> results.csv
# done
