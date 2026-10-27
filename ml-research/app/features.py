from __future__ import annotations

import numpy as np
import pandas as pd


FEATURE_COLUMNS = [
    "return_1",
    "return_5",
    "range_pct",
    "body_pct",
    "volume_change",
    "rolling_volatility_5",
    "volume_ratio_5",
]
TARGET_COLUMN = "next_abs_return"


def build_features(bars: pd.DataFrame) -> pd.DataFrame:
    """特征只依赖 t 时刻及此前数据；标签是 t+1 的绝对收益率。"""
    frame = bars.copy()
    frame["return_1"] = frame["close"].pct_change()
    frame["return_5"] = frame["close"].pct_change(5)
    frame["range_pct"] = (frame["high"] - frame["low"]) / frame["close"]
    frame["body_pct"] = (frame["close"] - frame["open"]) / frame["open"]
    frame["volume_change"] = frame["volume"].pct_change().clip(-5, 5)
    frame["rolling_volatility_5"] = frame["return_1"].rolling(5).std()
    frame["volume_ratio_5"] = frame["volume"] / frame["volume"].rolling(5).mean()
    frame[TARGET_COLUMN] = frame["return_1"].shift(-1).abs()
    frame = frame.replace([np.inf, -np.inf], np.nan)
    return frame.dropna(subset=FEATURE_COLUMNS + [TARGET_COLUMN]).reset_index(drop=True)
