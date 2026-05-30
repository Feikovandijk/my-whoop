"""Metric explanation payloads for derived daily stats.

These helpers do not recompute metrics. They describe the already-persisted
daily row, the input counts around that day, and the known limits of the
current algorithms so clients can render methodology and trust labels.
"""
from __future__ import annotations

import datetime as _dt
from typing import Any


def _iso_day(value: Any) -> str:
    if isinstance(value, _dt.date):
        return value.isoformat()
    return str(value)


def _metric(value: Any, algorithm: str, status: str, inputs: dict[str, Any],
            window: dict[str, Any] | None = None, limitation: str | None = None) -> dict[str, Any]:
    out: dict[str, Any] = {
        "value": value,
        "algorithm": algorithm,
        "status": status,
        "inputs": inputs,
    }
    if window is not None:
        out["window"] = window
    if limitation:
        out["limitation"] = limitation
    return out


def build_daily_explanation(
    daily_row: dict[str, Any],
    stream_counts: dict[str, int],
    sleep_session_count: int,
    exercise_session_count: int,
) -> dict[str, Any]:
    day = _iso_day(daily_row["day"])
    sleep_window = {
        "start": daily_row.get("sleep_start"),
        "end": daily_row.get("sleep_end"),
        "basis": "sleep session ending on this UTC date",
    }
    strain_window = {
        "basis": "wake to next sleep onset, falling back to the UTC calendar day",
        "sleep_end": daily_row.get("sleep_end"),
    }

    return {
        "device_id": daily_row["device_id"],
        "date": day,
        "data_quality": {
            "stream_counts": stream_counts,
            "sleep_sessions": sleep_session_count,
            "exercise_sessions": exercise_session_count,
        },
        "metrics": {
            "sleep": _metric(
                daily_row.get("total_sleep_min"),
                "sleep_detection_motion_hr_rr",
                "approx",
                {
                    "sleep_sessions": sleep_session_count,
                    "hr_samples": stream_counts.get("hr", 0),
                    "rr_samples": stream_counts.get("rr", 0),
                    "gravity_samples": stream_counts.get("gravity", 0),
                },
                window=sleep_window,
                limitation="Approximate wearable sleep staging from HR, RR, and movement.",
            ),
            "recovery": _metric(
                daily_row.get("recovery"),
                "personal_baseline_hrv_rhr_resp_sleep_logistic",
                "approx" if daily_row.get("recovery") is not None else "pending",
                {
                    "rr_samples": stream_counts.get("rr", 0),
                    "resp_samples": stream_counts.get("resp", 0),
                    "sleep_sessions": sleep_session_count,
                    "baseline": "trailing daily metrics",
                },
                window=sleep_window,
                limitation="Approximate recovery score from personal baselines, not WHOOP proprietary output.",
            ),
            "strain": _metric(
                daily_row.get("strain"),
                "hrr_edwards_trimp_log21",
                "approx" if daily_row.get("strain") is not None else "pending",
                {
                    "hr_samples": stream_counts.get("hr", 0),
                    "exercise_sessions": exercise_session_count,
                    "resting_hr": daily_row.get("resting_hr"),
                    "exercise_count": daily_row.get("exercise_count"),
                },
                window=strain_window,
                limitation=(
                    "Cardiovascular only. Uses HRR-based Edwards TRIMP and a logarithmic 0-21 "
                    "scale; does not include WHOOP's muscular strain component."
                ),
            ),
            "nightly_biometrics": _metric(
                {
                    "spo2_pct": daily_row.get("spo2_pct"),
                    "skin_temp_dev_c": daily_row.get("skin_temp_dev_c"),
                    "resp_rate_bpm": daily_row.get("resp_rate_bpm"),
                },
                "uncalibrated_type47_nightly_signals",
                "approx",
                {
                    "spo2_samples": stream_counts.get("spo2", 0),
                    "skin_temp_samples": stream_counts.get("skin_temp", 0),
                    "resp_samples": stream_counts.get("resp", 0),
                },
                window=sleep_window,
                limitation="Uncalibrated sensor conversions until fitted against reference data.",
            ),
        },
    }
