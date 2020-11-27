#!/usr/bin/env bash

gunicorn -k uvicorn.workers.UvicornWorker shortener.main:app --bind 0.0.0.0:8001 --workers 4 --threads 8
