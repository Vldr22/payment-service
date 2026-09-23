#!/bin/bash

echo "Starting Stripe listener..."
stripe listen --all-snapshot --forward-to localhost:8080/api/v1/webhooks/stripe