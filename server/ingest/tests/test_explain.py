from datetime import date


def test_build_daily_explanation_marks_strain_as_cardiovascular_only():
    from app.analysis.explain import build_daily_explanation

    daily_row = {
        "device_id": "devExplain",
        "day": date(2025, 10, 30),
        "total_sleep_min": 430.0,
        "efficiency": 0.91,
        "resting_hr": 49,
        "avg_hrv": 71.2,
        "recovery": 76.0,
        "strain": 12.4,
        "exercise_count": 1,
        "sleep_start": "2025-10-30T00:30:00+00:00",
        "sleep_end": "2025-10-30T08:00:00+00:00",
        "spo2_pct": 96.7,
        "skin_temp_dev_c": 0.2,
        "resp_rate_bpm": 15.8,
    }
    stream_counts = {
        "hr": 12345,
        "rr": 800,
        "events": 4,
        "battery": 3,
        "spo2": 100,
        "skin_temp": 100,
        "resp": 100,
        "gravity": 100,
    }

    out = build_daily_explanation(
        daily_row=daily_row,
        stream_counts=stream_counts,
        sleep_session_count=1,
        exercise_session_count=1,
    )

    assert out["date"] == "2025-10-30"
    assert out["metrics"]["strain"]["algorithm"] == "hrr_edwards_trimp_log21"
    assert out["metrics"]["strain"]["status"] == "approx"
    assert out["metrics"]["strain"]["inputs"]["hr_samples"] == 12345
    assert out["metrics"]["strain"]["inputs"]["exercise_sessions"] == 1
    assert "cardiovascular only" in out["metrics"]["strain"]["limitation"].lower()
    assert out["metrics"]["sleep"]["inputs"]["sleep_sessions"] == 1
    assert out["metrics"]["recovery"]["inputs"]["rr_samples"] == 800
    assert out["data_quality"]["stream_counts"]["resp"] == 100
