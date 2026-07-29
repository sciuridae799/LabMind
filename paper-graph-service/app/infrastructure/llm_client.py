from __future__ import annotations

import httpx

from app.config import Settings

LLM_TIMEOUT_SECONDS = 180.0


class GraphExtractionModelClient:
    def __init__(self, settings: Settings) -> None:
        self._url = settings.llm_chat_completions_url
        self._api_key = settings.llm_api_key
        self._model = settings.llm_model

    def extract(self, prompt: str) -> str:
        response = httpx.post(
            self._url,
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": self._model,
                "messages": [{"role": "user", "content": prompt}],
                "response_format": {"type": "json_object"},
                "temperature": 0,
                "stream": False,
            },
            timeout=LLM_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        payload = response.json()
        try:
            content = payload["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as error:
            raise ValueError("LLM response does not contain choices[0].message.content") from error
        if not isinstance(content, str) or not content.strip():
            raise ValueError("LLM response content is empty")
        return content.strip()
