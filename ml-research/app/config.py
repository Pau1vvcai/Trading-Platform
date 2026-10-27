from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    """所有外部地址都通过环境变量注入，测试无需依赖真实服务。"""

    trading_api_url: str
    result_callback_url: str
    artifact_dir: Path

    @classmethod
    def from_environment(cls) -> "Settings":
        api = os.getenv("TRADING_API_URL", "http://localhost:8080").rstrip("/")
        callback = os.getenv(
            "RESULT_CALLBACK_URL", f"{api}/api/v1/research-results"
        )
        return cls(api, callback, Path(os.getenv("ML_ARTIFACT_DIR", "./artifacts")))
