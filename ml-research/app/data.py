from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx
import numpy as np
import pandas as pd

from .schemas import DatasetManifest


def _value(raw: Any) -> Any:
    """兼容 Java 值对象序列化出的 {"value": ...} 与普通标量。"""
    return raw.get("value") if isinstance(raw, dict) and "value" in raw else raw


class MarketDataClient:
    def __init__(self, base_url: str, client: httpx.Client | None = None) -> None:
        self._base_url = base_url.rstrip("/")
        self._client = client or httpx.Client(timeout=20.0)

    def fetch(self, symbol: str, from_time: datetime, to_time: datetime) -> list[dict[str, Any]]:
        response = self._client.get(
            f"{self._base_url}/api/v1/market-data/bars",
            params={"symbol": symbol, "from": from_time.isoformat(), "to": to_time.isoformat()},
        )
        response.raise_for_status()
        payload = response.json()
        if not isinstance(payload, list):
            raise ValueError("market data response must be a list")
        return payload


def clean_bars(bars: list[dict[str, Any]], expected_symbol: str) -> pd.DataFrame:
    """排序、去重并剔除不满足 OHLCV 约束的行，不使用未来值填充。"""
    records = [
        {
            "timestamp": row.get("timestamp"),
            "symbol": str(_value(row.get("symbol", expected_symbol))).replace("/", "-"),
            "open": _value(row.get("open")),
            "high": _value(row.get("high")),
            "low": _value(row.get("low")),
            "close": _value(row.get("close")),
            "volume": _value(row.get("volume")),
        }
        for row in bars
    ]
    frame = pd.DataFrame.from_records(records)
    if frame.empty:
        raise ValueError("no market bars were returned")
    frame["timestamp"] = pd.to_datetime(frame["timestamp"], utc=True, errors="coerce")
    for column in ["open", "high", "low", "close", "volume"]:
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    frame = frame.replace([np.inf, -np.inf], np.nan).dropna()
    frame = frame[frame["symbol"].str.upper() == expected_symbol.replace("/", "-").upper()]
    valid = (
        (frame[["open", "high", "low", "close", "volume"]] > 0).all(axis=1)
        & (frame["high"] >= frame[["open", "close"]].max(axis=1))
        & (frame["low"] <= frame[["open", "close"]].min(axis=1))
    )
    frame = frame.loc[valid].drop_duplicates("timestamp", keep="last").sort_values("timestamp")
    if len(frame) < 30:
        raise ValueError("at least 30 valid bars are required")
    return frame.reset_index(drop=True)


def dataset_version(frame: pd.DataFrame) -> str:
    canonical = frame.to_csv(index=False, date_format="%Y-%m-%dT%H:%M:%S.%fZ", float_format="%.10g")
    return "sha256-" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


class DatasetStore:
    def __init__(self, root: Path) -> None:
        self._root = root

    def save(self, frame: pd.DataFrame, symbol: str, source: str) -> DatasetManifest:
        version = dataset_version(frame)
        directory = self._root / "datasets"
        directory.mkdir(parents=True, exist_ok=True)
        frame.to_csv(directory / f"{version}.csv", index=False)
        manifest = DatasetManifest(
            dataset_version=version,
            symbol=symbol,
            row_count=len(frame),
            **{"from": frame.iloc[0]["timestamp"].to_pydatetime()},
            **{"to": frame.iloc[-1]["timestamp"].to_pydatetime()},
            source=source,
            cleaning_policy="UTC sort; timestamp deduplication; invalid OHLCV rows dropped; no future fill",
            created_at=datetime.now(timezone.utc),
        )
        (directory / f"{version}.json").write_text(
            json.dumps(manifest.model_dump(by_alias=True, mode="json"), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return manifest
