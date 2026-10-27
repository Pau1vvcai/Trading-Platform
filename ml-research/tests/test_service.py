from __future__ import annotations

from datetime import datetime, timezone

from app.data import DatasetStore
from app.schemas import ExperimentRequest
from app.service import ExperimentService


class FakeMarketData:
    def __init__(self, rows):
        self.rows = rows

    def fetch(self, symbol, from_time, to_time):
        return self.rows


class RecordingPublisher:
    def __init__(self):
        self.result = None

    def publish(self, result):
        self.result = result


def test_experiment_saves_versioned_artifacts_and_publishes_result(tmp_path, bars_frame):
    publisher = RecordingPublisher()
    service = ExperimentService(
        FakeMarketData(bars_frame.to_dict("records")),
        DatasetStore(tmp_path),
        publisher,
        tmp_path,
    )
    result = service.run(
        ExperimentRequest(
            symbol="BTC-USD",
            **{
                "from": datetime(2026, 1, 1, tzinfo=timezone.utc),
                "to": datetime(2027, 1, 1, tzinfo=timezone.utc),
            },
            folds=3,
            min_train_size=40,
        )
    )

    assert result.target == "next_abs_return"
    assert result.fold_count == 3
    assert publisher.result == result
    assert (tmp_path / "datasets" / f"{result.dataset_version}.json").exists()
    assert (tmp_path / "models" / f"{result.experiment_id}.joblib").exists()
    assert (tmp_path / "experiments" / f"{result.experiment_id}.json").exists()
