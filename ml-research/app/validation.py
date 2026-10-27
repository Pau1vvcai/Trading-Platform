from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from .features import FEATURE_COLUMNS, TARGET_COLUMN
from .schemas import FoldMetric


@dataclass(frozen=True)
class ValidationOutcome:
    baseline_mae: float
    model_mae: float
    folds: list[FoldMetric]
    fitted_model: Pipeline


def _model() -> Pipeline:
    # 缩放器位于 Pipeline 内，因此每个窗口只用自己的训练集拟合。
    return Pipeline([("scale", StandardScaler()), ("ridge", Ridge(alpha=1.0))])


def walk_forward_validate(
    frame: pd.DataFrame, folds: int, min_train_size: int
) -> ValidationOutcome:
    if len(frame) < min_train_size + folds * 5:
        raise ValueError(
            f"need at least {min_train_size + folds * 5} feature rows for {folds} folds"
        )
    test_size = (len(frame) - min_train_size) // folds
    fold_metrics: list[FoldMetric] = []
    all_actual: list[np.ndarray] = []
    all_baseline: list[np.ndarray] = []
    all_model: list[np.ndarray] = []

    for index in range(folds):
        train_end = min_train_size + index * test_size
        test_end = len(frame) if index == folds - 1 else train_end + test_size
        train = frame.iloc[:train_end]
        test = frame.iloc[train_end:test_end]
        estimator = _model()
        estimator.fit(train[FEATURE_COLUMNS], train[TARGET_COLUMN])
        prediction = np.clip(estimator.predict(test[FEATURE_COLUMNS]), 0.0, None)
        baseline_value = float(train[TARGET_COLUMN].mean())
        baseline = np.full(len(test), baseline_value)
        actual = test[TARGET_COLUMN].to_numpy()
        all_actual.append(actual)
        all_baseline.append(baseline)
        all_model.append(prediction)
        fold_metrics.append(
            FoldMetric(
                fold=index + 1,
                train_from=train.iloc[0]["timestamp"].to_pydatetime(),
                train_to=train.iloc[-1]["timestamp"].to_pydatetime(),
                test_from=test.iloc[0]["timestamp"].to_pydatetime(),
                test_to=test.iloc[-1]["timestamp"].to_pydatetime(),
                train_samples=len(train),
                test_samples=len(test),
                baseline_mae=float(mean_absolute_error(actual, baseline)),
                model_mae=float(mean_absolute_error(actual, prediction)),
            )
        )

    actual = np.concatenate(all_actual)
    baseline = np.concatenate(all_baseline)
    prediction = np.concatenate(all_model)
    final_model = _model().fit(frame[FEATURE_COLUMNS], frame[TARGET_COLUMN])
    return ValidationOutcome(
        baseline_mae=float(mean_absolute_error(actual, baseline)),
        model_mae=float(mean_absolute_error(actual, prediction)),
        folds=fold_metrics,
        fitted_model=final_model,
    )
