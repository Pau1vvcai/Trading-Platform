from __future__ import annotations

import httpx
from fastapi import FastAPI, HTTPException

from .config import Settings
from .data import DatasetStore, MarketDataClient
from .schemas import ExperimentRequest, ExperimentResult
from .service import ExperimentService, ResultPublisher


def create_app(settings: Settings | None = None) -> FastAPI:
    config = settings or Settings.from_environment()
    service = ExperimentService(
        MarketDataClient(config.trading_api_url),
        DatasetStore(config.artifact_dir),
        ResultPublisher(config.result_callback_url),
        config.artifact_dir,
    )
    app = FastAPI(title="Trading ML Research", version="0.3.0")

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.post("/api/v1/experiments", response_model=ExperimentResult)
    def run_experiment(request: ExperimentRequest) -> ExperimentResult:
        try:
            return service.run(request)
        except (ValueError, httpx.HTTPError) as error:
            raise HTTPException(status_code=422, detail=str(error)) from error

    return app


# Uvicorn 使用的默认应用；测试可通过 create_app 注入隔离配置。
app = create_app()
