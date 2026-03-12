"""
push_logs_to_api.py
===================

Generates logs using the internal generator and pushes them
to the LogStream ingestion API instead of saving them to file.

Flow:
generator -> transform -> POST /api/logs/batch
"""

import requests
import time
from scripts.data_generator import generate_logs
from config.config import SERVICES
import os 
from dotenv import load_dotenv 
import random 


load_dotenv()



API_URL_BATCH = os.getenv("API_URL_BATCH")
BATCH_SIZE = 500


def transform_log(log: dict) -> dict:
    """
    Convert generator schema to API schema.
    """

    return {
        "serviceName": log["service_name"],
        "level": log["level"],
        "message": log["message"],
        "source": log["source"],
    }


def chunk_list(data, size):
    """Yield chunks from a list."""
    for i in range(0, len(data), size):
        yield data[i:i + size]


def push_logs(logs: list):
    """
    Send logs to API in batches.
    """

    total_sent = 0

    for batch in chunk_list(logs, BATCH_SIZE):

        payload = {
            "logs": [transform_log(log) for log in batch]
        }

        response = requests.post(API_URL_BATCH, json=payload)

        if response.status_code not in (200, 201):
            print(f"[ERROR] Failed batch: {response.status_code}")
            print(response.text)
            continue

        total_sent += len(batch)
        print(f"[API] Sent {total_sent:,} logs")

        # small delay so we don't overwhelm the API
        time.sleep(0.2)

    print(f"\nFinished sending {total_sent:,} logs")






def main():

    services = list(SERVICES.keys())
    print("Generating logs...")
    num_log = random.randint(50,200)
    logs = generate_logs(services, num_logs=num_log, days=30)

    print(f"Generated {len(logs):,} logs")

    push_logs(logs)


if __name__ == "__main__":
    main()