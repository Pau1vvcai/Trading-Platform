from __future__ import annotations

from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ExperimentRequest(ApiModel):
    symbol: str = "BTC-USD"
    from_time: datetime = Field(alias="from")
    to_time: datetime = Field(alias="to")
    model: Literal["RIDGE"] = "RIDGE"
    folds: Annotated[int, Field(ge=2, le=10)] = 3
    min_train_size: Annotated[int, Field(ge=20)] = 40


class DatasetManifest(ApiModel):
    dataset_version: str
    symbol: str
    row_count: int
    from_time: datetime = Field(alias="from")
    to_time: datetime = Field(alias="to")
    source: str
    cleaning_policy: str
    created_at: datetime


class FoldMetric(ApiModel):
    fold: int
    train_from: datetime
    train_to: datetime
    test_from: datetime
    test_to: datetime
    train_samples: int
    test_samples: int
    baseline_mae: float
    model_mae: float


class ExperimentResult(ApiModel):
    experiment_id: str
    dataset_version: str
    symbol: str
    target: str
    model: str
    sample_count: int
    fold_count: int
    baseline_mae: float
    model_mae: float
    improvement_pct: float
    created_at: datetime
    leakage_policy: str
    cost_assumption: str
    folds: list[FoldMetric]
