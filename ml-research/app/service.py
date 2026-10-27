from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

import httpx
import joblib

from .data import DatasetStore, MarketDataClient, clean_bars
from .features import TARGET_COLUMN, build_features
from .schemas import ExperimentRequest, ExperimentResult
from .validation import walk_forward_validate


class ResultPublisher:
    def __init__(self, callback_url: str, client: httpx.Client | None = None) -> None:
        self._callback_url = callback_url
        self._client = client or httpx.Client(timeout=20.0)

    def publish(self, result: ExperimentResult) -> None:
        response = self._client.post(
            self._callback_url, json=result.model_dump(by_alias=True, mode="json")
        )
        response.raise_for_status()


class ExperimentService:
    def __init__(
        self,
        market_data: MarketDataClient,
        datasets: DatasetStore,
        publisher: ResultPublisher,
        artifact_dir: Path,
    ) -> None:
        self._market_data = market_data
        self._datasets = datasets
        self._publisher = publisher
        self._artifact_dir = artifact_dir

    def run(self, request: ExperimentRequest) -> ExperimentResult:
        if request.from_time >= request.to_time:
            raise ValueError("from must be earlier than to")
        raw = self._market_data.fetch(request.symbol, request.from_time, request.to_time)
        bars = clean_bars(raw, request.symbol)
        manifest = self._datasets.save(
            bars,
            request.symbol,
            f"{request.symbol}:{request.from_time.isoformat()}:{request.to_time.isoformat()}",
        )
        features = build_features(bars)
        outcome = walk_forward_validate(features, request.folds, request.min_train_size)
        experiment_id = str(uuid4())
        improvement = (
            0.0
            if outcome.baseline_mae == 0
            else (outcome.baseline_mae - outcome.model_mae) / outcome.baseline_mae * 100
        )
        result = ExperimentResult(
            experiment_id=experiment_id,
            dataset_version=manifest.dataset_version,
            symbol=request.symbol,
            target=TARGET_COLUMN,
            model=request.model,
            sample_count=len(features),
            fold_count=len(outcome.folds),
            baseline_mae=outcome.baseline_mae,
            model_mae=outcome.model_mae,
            improvement_pct=improvement,
            created_at=datetime.now(timezone.utc),
            leakage_policy=(
                "features at t use t/past bars only; target is |return(t+1)|; "
                "scaler and model are fitted inside each expanding training window"
            ),
            cost_assumption=(
                "volatility prediction only; no orders are generated, so fees and slippage are not applied"
            ),
            folds=outcome.folds,
        )
        self._save_artifacts(result, outcome.fitted_model)
        self._publisher.publish(result)
        return result

    def _save_artifacts(self, result: ExperimentResult, model: object) -> None:
        model_dir = self._artifact_dir / "models"
        experiment_dir = self._artifact_dir / "experiments"
        model_dir.mkdir(parents=True, exist_ok=True)
        experiment_dir.mkdir(parents=True, exist_ok=True)
        joblib.dump(model, model_dir / f"{result.experiment_id}.joblib")
        (experiment_dir / f"{result.experiment_id}.json").write_text(
            json.dumps(result.model_dump(by_alias=True, mode="json"), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
