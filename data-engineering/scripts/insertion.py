import pandas as pd
import json
from sqlalchemy import create_engine
from pathlib import Path
from config.config import DATABASE_URL

# correct path
DATA_DIR = Path("data")

json_files = list(DATA_DIR.glob("*.json"))

if not json_files:
    raise FileNotFoundError("No JSON files found in data directory")

latest_file = max(json_files, key=lambda f: f.stat().st_mtime)

print(f"Loading {latest_file}...")

with open(latest_file, "r") as f:
    logs = json.load(f)

df = pd.DataFrame(logs)

print("Connecting to database...")
engine = create_engine(DATABASE_URL)

print("Inserting records...")

df.to_sql(
    "log_entries",
    engine,
    if_exists="append",
    index=False,
    method="multi",
    chunksize=1000
)

print(f"Inserted {len(df)} rows successfully.")