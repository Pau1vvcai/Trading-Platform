from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pandas as pd
import pytest


@pytest.fixture
def bars_frame() -> pd.DataFrame:
    rows = []
    previous = 100.0
    start = datetime(2026, 1, 1, tzinfo=timezone.utc)
    for index in range(100):
        close = 100 + index * 0.2 + ((index % 7) - 3) * 0.35
        opened = previous
        rows.append(
            {
                "timestamp": start + timedelta(days=index),
                "symbol": "BTC-USD",
                "open": opened,
                "high": max(opened, close) + 1,
                "low": min(opened, close) - 1,
                "close": close,
                "volume": 1000 + index * 5 + (index % 4) * 30,
            }
        )
        previous = close
    return pd.DataFrame(rows)
