#!/bin/bash
## Запустить stripe listen
stripe listen --all-snapshot --forward-to localhost:8080/api/webhooks/stripe