from __future__ import annotations

import pytest

from app.features import FEATURE_COLUMNS, TARGET_COLUMN, build_features


def test_features_use_present_and_past_while_label_uses_next_bar(bars_frame):
    original = build_features(bars_frame)
    changed = bars_frame.copy()
    changed.loc[len(changed) - 1, "close"] *= 1.5
    rebuilt = build_features(changed)

    # 改变最后一根 K 线，较早行的特征不应变化；倒数第二行标签应变化。
    early_timestamp = original.iloc[-3]["timestamp"]
    early_original = original[original["timestamp"] == early_timestamp].iloc[0]
    early_rebuilt = rebuilt[rebuilt["timestamp"] == early_timestamp].iloc[0]
    for column in FEATURE_COLUMNS:
        assert early_original[column] == pytest.approx(early_rebuilt[column])

    label_timestamp = original.iloc[-1]["timestamp"]
    old_label = original[original["timestamp"] == label_timestamp].iloc[0][TARGET_COLUMN]
    new_label = rebuilt[rebuilt["timestamp"] == label_timestamp].iloc[0][TARGET_COLUMN]
    assert old_label != new_label


def test_feature_table_has_no_missing_values(bars_frame):
    features = build_features(bars_frame)
    assert len(features) == len(bars_frame) - 6
    assert not features[FEATURE_COLUMNS + [TARGET_COLUMN]].isna().any().any()
