from __future__ import annotations

import pytest

from app.features import build_features
from app.validation import walk_forward_validate


def test_walk_forward_uses_expanding_non_overlapping_windows(bars_frame):
    outcome = walk_forward_validate(build_features(bars_frame), folds=3, min_train_size=40)

    assert len(outcome.folds) == 3
    assert [fold.train_samples for fold in outcome.folds] == sorted(
        fold.train_samples for fold in outcome.folds
    )
    assert all(fold.train_to < fold.test_from for fold in outcome.folds)
    assert all(fold.baseline_mae >= 0 and fold.model_mae >= 0 for fold in outcome.folds)
    assert outcome.baseline_mae >= 0
    assert outcome.model_mae >= 0


def test_walk_forward_rejects_too_few_samples(bars_frame):
    with pytest.raises(ValueError, match="need at least"):
        walk_forward_validate(build_features(bars_frame).head(45), folds=3, min_train_size=40)
