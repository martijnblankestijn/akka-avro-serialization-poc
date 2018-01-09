#!/usr/bin/env bash
IMAGE=cassandra:3
IMAGE_NAME=cassandra-akka
docker run --rm --name $IMAGE_NAME -p 9042:9042 -d $IMAGE
