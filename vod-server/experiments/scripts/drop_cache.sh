#!/bin/bash

echo "Dropping page cache..."
sync
echo 3 | sudo tee /proc/sys/vm/drop_caches
sleep 5
echo "Done."
