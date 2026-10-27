from __future__ import annotations

from app.data import clean_bars, dataset_version


def test_clean_bars_sorts_deduplicates_and_drops_invalid_rows(bars_frame):
    rows = bars_frame.head(35).to_dict("records")
    rows.reverse()
    rows.append(dict(rows[-1]))
    rows.append(
        {
            "timestamp": "2026-04-01T00:00:00Z",
            "symbol": "BTC-USD",
            "open": 100,
            "high": 90,
            "low": 80,
            "close": 95,
            "volume": 1,
        }
    )

    cleaned = clean_bars(rows, "BTC-USD")

    assert len(cleaned) == 35
    assert cleaned["timestamp"].is_monotonic_increasing
    assert cleaned["timestamp"].is_unique


def test_dataset_version_is_content_addressed(bars_frame):
    first = dataset_version(bars_frame)
    second = dataset_version(bars_frame.copy())
    changed = bars_frame.copy()
    changed.loc[0, "close"] += 1

    assert first == second
    assert first.startswith("sha256-")
    assert first != dataset_version(changed)
