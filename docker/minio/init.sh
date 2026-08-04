#!/bin/sh
set -eu

alias_name=lease-local
bucket_name="${MINIO_BUCKET:-lease}"

mc alias set "$alias_name" http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
mc mb --ignore-existing "$alias_name/$bucket_name"
mc anonymous set download "$alias_name/$bucket_name"
mc cp /init/demo-room.jpg "$alias_name/$bucket_name/demo-room.jpg"
