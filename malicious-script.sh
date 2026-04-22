#!/bin/bash
echo "This is a malicious script that would execute during build"
echo "CACHE_POISON_MARKER_$(date +%s)" > /tmp/cache-poison-marker.txt
# In a real attack, this would exfiltrate data or persist malicious code
echo "Cache poisoning payload executed"