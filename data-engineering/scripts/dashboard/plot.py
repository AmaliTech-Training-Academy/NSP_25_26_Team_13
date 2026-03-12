"""
dashboard.py  ·  LogStream Observability Platform
==================================================
Professional light-theme analytics dashboard — Plotly Dash.
Clean, editorial design language. Data-forward. Minimal chrome.

Typography  : DM Sans (labels) + JetBrains Mono (data/numbers)
Palette     : Crisp white base · slate greys · indigo primary · semantic accents
Layout      : Structured sections with clear visual hierarchy

Run:
    python scripts/dashboard.py --data data/logs_YYYYMMDD_HHMMSS.json
    python scripts/dashboard.py          # uses generated sample data
    http://127.0.0.1:8050
"""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import argparse
import random
import uuid
from datetime import datetime, timedelta, timezone

import pandas as pd
import plotly.graph_objects as go
from dash import Dash, dcc, html, Input, Output
import os
from sqlalchemy import create_engine
from dotenv import load_dotenv

load_dotenv()



from config.config import (
    SERVICES, LEVELS, LEVEL_WEIGHTS, _HOUR_WEIGHTS, MESSAGE_MAP,
)
from scripts.data_analytics import (
    validate,
    error_rate_24h,
    common_errors_top_n,
    volume_trends_hourly,
    volume_trends_daily,
    warn_rate_24h,
    level_distribution,
    activity_heatmap,
    error_spike_detection,
    silent_services,
    top_noisy_services,
    mean_time_between_errors,
    recent_critical_events,
    service_health_summary,
    _window,
)


# ═══════════════════════════════════════════════════════════════════════════════
#  DESIGN SYSTEM  —  Light / Editorial
# ═══════════════════════════════════════════════════════════════════════════════

C = {
    # ── Page layers ──────────────────────────────────────────────────────────
    "page":          "#F5F6FA",    # outermost background
    "surface":       "#FFFFFF",    # cards / panels
    "surface_alt":   "#F9FAFB",    # subtle inset / alternating rows
    "border":        "#E4E7EF",    # card borders
    "border_mid":    "#CBD0DC",    # stronger dividers
    "header_bg":     "#FFFFFF",    # sticky header

    # ── Primary — indigo ─────────────────────────────────────────────────────
    "indigo":        "#4F6EF7",
    "indigo_light":  "#EEF1FE",
    "indigo_mid":    "#C7D0FB",
    "indigo_dark":   "#3352D4",

    # ── Semantic ─────────────────────────────────────────────────────────────
    "red":           "#E63950",
    "red_light":     "#FEF0F2",
    "red_mid":       "#FBBCC4",
    "orange":        "#F07630",
    "orange_light":  "#FEF4EE",
    "orange_mid":    "#FACCAA",
    "amber":         "#D97706",
    "amber_light":   "#FFFBEB",
    "green":         "#16A34A",
    "green_light":   "#F0FDF4",
    "green_mid":     "#BBF7D0",
    "teal":          "#0891B2",
    "teal_light":    "#ECFEFF",
    "purple":        "#7C3AED",
    "purple_light":  "#F5F3FF",

    # ── Service palette ───────────────────────────────────────────────────────
    "svc0":          "#4F6EF7",   # api-gateway   — indigo
    "svc1":          "#16A34A",   # auth-service  — green
    "svc2":          "#F07630",   # notification  — orange
    "svc3":          "#0891B2",   # order-service — teal
    "svc4":          "#7C3AED",   # payment       — purple
    "svc5":          "#D97706",   # user-service  — amber

    # ── Level palette ─────────────────────────────────────────────────────────
    "lvl_error":     "#E63950",
    "lvl_warn":      "#F07630",
    "lvl_info":      "#4F6EF7",
    "lvl_debug":     "#7C3AED",
    "lvl_trace":     "#94A3B8",

    # ── Typography ────────────────────────────────────────────────────────────
    "text_primary":  "#0F1729",
    "text_secondary":"#4B5675",
    "text_tertiary": "#8B96AD",
    "text_muted":    "#B8C0CF",

    # ── Chart grid / axes ─────────────────────────────────────────────────────
    "grid":          "#EEF0F6",
    "axis":          "#D1D5E0",
}

LVL_C = {
    "ERROR": C["lvl_error"],
    "WARN":  C["lvl_warn"],
    "INFO":  C["lvl_info"],
    "DEBUG": C["lvl_debug"],
    "TRACE": C["lvl_trace"],
}

SVC_PALETTE = [C["svc0"], C["svc1"], C["svc2"], C["svc3"], C["svc4"], C["svc5"]]

MONO  = "'JetBrains Mono', 'Fira Code', 'Courier New', monospace"
SANS  = "'DM Sans', 'Inter', system-ui, sans-serif"

# ── Plotly base layout ────────────────────────────────────────────────────────
_BASE = dict(
    paper_bgcolor=C["surface"],
    plot_bgcolor=C["surface"],
    font=dict(family=SANS, color=C["text_secondary"], size=12),
    margin=dict(l=16, r=16, t=44, b=16),
    hoverlabel=dict(
        bgcolor=C["text_primary"],
        bordercolor=C["text_primary"],
        font=dict(family=MONO, size=11, color="#FFFFFF"),
    ),
)
_XA = dict(
    gridcolor=C["grid"], linecolor=C["axis"], zeroline=False,
    tickfont=dict(size=10, color=C["text_tertiary"], family=MONO),
    title_font=dict(size=11, color=C["text_secondary"], family=SANS),
    showgrid=True,
)
_YA = dict(
    gridcolor=C["grid"], linecolor=C["axis"], zeroline=False,
    tickfont=dict(size=10, color=C["text_tertiary"], family=MONO),
    title_font=dict(size=11, color=C["text_secondary"], family=SANS),
    showgrid=True,
)
_LG = dict(
    bgcolor=C["surface"],
    bordercolor=C["border"],
    borderwidth=1,
    font=dict(size=11, color=C["text_secondary"], family=SANS),
)


def _layout(h=300, title="", xa=None, ya=None, lg=None, **kw):
    return {
        **_BASE, "height": h,
        "title": dict(
            text=title,
            font=dict(size=12, color=C["text_primary"], family=SANS, weight=600),
            x=0.0, pad=dict(l=0, b=6),
        ),
        "xaxis":  {**_XA, **(xa or {})},
        "yaxis":  {**_YA, **(ya or {})},
        "legend": {**_LG, **(lg or {})},
        **kw,
    }


def _hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def _rgba(hex_color, alpha):
    r, g, b = _hex_rgb(hex_color)
    return f"rgba({r},{g},{b},{alpha})"


def _empty(msg="No data available for this window", h=220):
    fig = go.Figure()
    fig.add_annotation(
        text=msg, x=0.5, y=0.5, showarrow=False, xref="paper", yref="paper",
        font=dict(color=C["text_tertiary"], size=12, family=SANS),
    )
    fig.update_layout(**_layout(h=h))
    return fig


# ═══════════════════════════════════════════════════════════════════════════════
#  SAMPLE DATA GENERATOR
# ═══════════════════════════════════════════════════════════════════════════════

def _sample_msg(level):
    t = random.choice(MESSAGE_MAP[level])
    parts = t.split("{n}")
    r = parts[0]
    for p in parts[1:]:
        r += str(random.randint(10, 999)) + p
    return r


def _generate_sample_df(n=9000, days=30):
    svcs  = list(SERVICES.keys())
    now   = datetime.now(timezone.utc).replace(tzinfo=None)
    start = now - timedelta(days=days)
    delta = int((now - start).total_seconds())
    rows  = []
    for _ in range(n):
        svc = random.choice(svcs)
        lvl = random.choices(LEVELS, weights=LEVEL_WEIGHTS)[0]
        for _ in range(3):
            ts = start + timedelta(seconds=random.randint(0, delta))
            if random.random() < _HOUR_WEIGHTS[ts.hour] / 2.5:
                break
        rows.append({
            "id": str(uuid.uuid4()), "timestamp": ts, "level": lvl,
            "source": svc, "message": _sample_msg(lvl),
            "service_name": svc, "created_at": ts,
        })
    df = pd.DataFrame(rows)
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    return df.sort_values("timestamp").reset_index(drop=True)


def load_df(path):
    if path is None:
        print("[logstream] No file specified — generating sample data")
        raw = _generate_sample_df()
    elif path.endswith(".json"):
        raw = pd.read_json(path)
    else:
        raw = pd.read_csv(path)
    df, report = validate(raw)
    if not report.passed():
        print(f"[logstream] Validation: {report.invalid_rows:,} rows dropped")
    return df


def load_df_postgres(days_back:int=30)->pd.DataFrame:
    dsn=(
        f"postgresql://{os.environ['DB_USERNAME']}:{os.environ['DB_PASSWORD']}"
        f"@{os.environ['DB_URL']}"
        f":5432/{os.environ['DB_NAME']}"
    )
    engine = create_engine(dsn, pool_pre_ping=True)
    query =f""" 
    SELECT
            id,
            timestamp,
            level,
            source,
            message,
            service_name,
            created_at
        FROM log_entries
        WHERE timestamp >= NOW() - INTERVAL '{days_back} days'
        ORDER BY timestamp ASC
    """
    raw = pd.read_sql(query, engine)
    raw["timestamp"] = pd.to_datetime(raw["timestamp"], utc=True).dt.tz_localize(None)
    raw["created_at"] = pd.to_datetime(raw["created_at"], utc=True).dt.tz_localize(None)
    df, report = validate(raw)
    if not report.passed():
        print(f"[logstream] Validation: {report.invalid_rows:,} rows dropped")
    return df


# ═══════════════════════════════════════════════════════════════════════════════
#  KPI HELPERS
# ═══════════════════════════════════════════════════════════════════════════════

def _kpis(df):
    w    = _window(df, hours=24)
    prev = _window(df, hours=48)
    if not w.empty:
        prev = prev[prev["timestamp"] < w["timestamp"].min()]
    total  = len(w)
    errors = int((w["level"] == "ERROR").sum())
    warns  = int((w["level"] == "WARN").sum())
    erate  = round(errors / max(total, 1) * 100, 1)
    e_prev = int((prev["level"] == "ERROR").sum())
    pct_chg = round((errors - e_prev) / max(e_prev, 1) * 100, 1)
    trend  = f"↑ {pct_chg}%" if errors > e_prev else (f"↓ {abs(pct_chg)}%" if errors < e_prev else "— flat")
    tc     = C["red"] if errors > e_prev else (C["green"] if errors < e_prev else C["text_tertiary"])
    return dict(
        total=total, errors=errors, warns=warns, erate=erate,
        services=df["service_name"].nunique(),
        throughput=round(total / 24, 1),
        trend=trend, trend_color=tc,
    )


# ═══════════════════════════════════════════════════════════════════════════════
#  CHART BUILDERS
# ═══════════════════════════════════════════════════════════════════════════════

# ── 1. Hourly volume timeline ─────────────────────────────────────────────────
def chart_volume_timeline(df, svc=None):
    data = volume_trends_hourly(df, service_name=svc, days_back=7)
    if data.empty:
        return _empty()
    fig = go.Figure()
    # Background layers: TRACE, DEBUG, INFO as soft filled areas
    for lvl, alpha_fill, alpha_line in [("TRACE", 0.08, 0.3), ("DEBUG", 0.12, 0.4), ("INFO", 0.18, 0.6)]:
        s = data[data["level"] == lvl]
        if s.empty:
            continue
        col = LVL_C[lvl]
        fig.add_trace(go.Scatter(
            x=s["hour"], y=s["log_count"], name=lvl,
            mode="lines", stackgroup="bg",
            line=dict(width=0),
            fillcolor=_rgba(col, alpha_fill),
            hovertemplate=f"<b>{lvl}</b>  %{{x|%a %d %H:00}}  ·  %{{y:,}} logs<extra></extra>",
        ))
    # WARN — dashed line
    s = data[data["level"] == "WARN"]
    if not s.empty:
        fig.add_trace(go.Scatter(
            x=s["hour"], y=s["log_count"], name="WARN",
            mode="lines",
            line=dict(color=C["lvl_warn"], width=1.5, dash="dot"),
            hovertemplate="<b>WARN</b>  %{x|%a %d %H:00}  ·  %{y:,}<extra></extra>",
        ))
    # ERROR — solid foreground with fill
    s = data[data["level"] == "ERROR"]
    if not s.empty:
        fig.add_trace(go.Scatter(
            x=s["hour"], y=s["log_count"], name="ERROR",
            mode="lines",
            line=dict(color=C["lvl_error"], width=2),
            fill="tozeroy",
            fillcolor=_rgba(C["lvl_error"], 0.07),
            hovertemplate="<b>ERROR</b>  %{x|%a %d %H:00}  ·  %{y:,}<extra></extra>",
        ))
    fig.update_layout(**_layout(
        h=300, title="Log Traffic  ·  7-Day Hourly View",
        xa=dict(tickformat="%a %b %d", dtick=86400000),
        lg=dict(orientation="h", yanchor="bottom", y=1.0, xanchor="right", x=1,
                font=dict(size=10)),
    ))
    return fig


# ── 2. Error rate per service ─────────────────────────────────────────────────
def chart_error_rate(df, svc=None):
    data = error_rate_24h(df, service_name=svc).sort_values("error_rate_percent")
    if data.empty:
        return _empty()

    def _c(r):
        if r >= 15: return C["red"]
        if r >= 8:  return C["orange"]
        if r >= 3:  return C["amber"]
        return C["green"]

    def _bg(r):
        if r >= 15: return C["red_light"]
        if r >= 8:  return C["orange_light"]
        if r >= 3:  return C["amber_light"]
        return C["green_light"]

    fig = go.Figure()
    # Track background
    fig.add_trace(go.Bar(
        x=[100] * len(data), y=data["service_name"],
        orientation="h",
        marker=dict(color=[_bg(r) for r in data["error_rate_percent"]], line=dict(width=0)),
        showlegend=False, hoverinfo="skip",
    ))
    # Value bar
    fig.add_trace(go.Bar(
        x=data["error_rate_percent"], y=data["service_name"],
        orientation="h",
        marker=dict(color=[_c(r) for r in data["error_rate_percent"]], line=dict(width=0)),
        text=[f"  {r:.1f}%" for r in data["error_rate_percent"]],
        textposition="outside",
        textfont=dict(size=11, color=C["text_primary"], family=MONO),
        customdata=data[["total_logs", "error_count"]].values,
        hovertemplate=(
            "<b>%{y}</b><br>"
            "Error rate : <b>%{x:.2f}%</b><br>"
            "Errors / Total : %{customdata[1]:,} / %{customdata[0]:,}<extra></extra>"
        ),
    ))
    fig.update_layout(
        **_layout(h=300, title="Error Rate by Service  ·  Last 24 Hours",
                  xa=dict(range=[0, 130], showgrid=False, showticklabels=False),
                  ya=dict(tickfont=dict(size=11, color=C["text_primary"], family=SANS))),
        barmode="overlay", bargap=0.3,
    )
    for x_val, lbl, col in [(5, "SLO 5%", C["amber"]), (10, "Critical 10%", C["red"])]:
        fig.add_vline(x=x_val, line_dash="dash", line_color=col, line_width=1.2, opacity=0.7,
                      annotation=dict(text=lbl, font=dict(size=9, color=col, family=SANS),
                                      yanchor="bottom", bgcolor="rgba(255,255,255,0.85)",
                                      borderpad=3))
    return fig


# ── 3. Level donut ────────────────────────────────────────────────────────────
def chart_level_donut(df, svc=None):
    w = _window(df, hours=24)
    if svc:
        w = w[w["service_name"] == svc]
    counts = w.groupby("level").size().reset_index(name="n")
    if counts.empty:
        return _empty()
    total   = counts["n"].sum()
    err_n   = counts.loc[counts["level"] == "ERROR", "n"].sum() if "ERROR" in counts["level"].values else 0
    erate   = round(err_n / max(total, 1) * 100, 1)
    ec      = C["red"] if erate > 10 else (C["orange"] if erate > 5 else C["green"])

    fig = go.Figure(go.Pie(
        labels=counts["level"], values=counts["n"],
        hole=0.70,
        marker=dict(
            colors=[LVL_C.get(l, C["text_muted"]) for l in counts["level"]],
            line=dict(color=C["surface"], width=3),
        ),
        textinfo="none",
        hovertemplate="<b>%{label}</b>  ·  %{value:,} events  (%{percent})<extra></extra>",
        sort=False,
    ))
    fig.update_layout(
        **_layout(h=260, title="Log Level Distribution  ·  24h",
                  lg=dict(orientation="v", x=1.0, font=dict(size=10))),
        annotations=[
            dict(text=f"<b>{erate}%</b>", x=0.5, y=0.57, showarrow=False,
                 xref="paper", yref="paper",
                 font=dict(size=26, color=ec, family=MONO, weight=700)),
            dict(text="error rate", x=0.5, y=0.42, showarrow=False,
                 xref="paper", yref="paper",
                 font=dict(size=10, color=C["text_tertiary"], family=SANS)),
        ],
    )
    return fig


# ── 4. Top 10 error messages ──────────────────────────────────────────────────
def chart_top_errors(df, svc=None):
    data = common_errors_top_n(df, top_n=10, service_name=svc, days_back=30).copy()
    if data.empty:
        return _empty()
    data["short"] = data["message"].str[:55].str.strip() + "…"
    data["label"] = (data["service_name"].str.replace("-service", "", regex=False)
                     + "  ›  " + data["short"])
    max_v = data["occurrences"].max()
    colors = [_rgba(C["red"], 0.15 + 0.75 * (v / max_v)) for v in data["occurrences"]]

    fig = go.Figure(go.Bar(
        x=data["occurrences"], y=data["label"],
        orientation="h",
        marker=dict(color=colors, line=dict(width=0)),
        text=data["occurrences"].apply(lambda n: f"{n:,}×"),
        textposition="outside",
        textfont=dict(size=10, color=C["text_secondary"], family=MONO),
        hovertemplate="<b>%{y}</b><br>Occurrences: %{x:,}<extra></extra>",
    ))
    fig.update_layout(**_layout(
        h=360, title="Top Error Messages  ·  30-Day Window",
        xa=dict(showgrid=False, showticklabels=False),
        ya=dict(tickfont=dict(size=10, color=C["text_secondary"], family=SANS),
                autorange="reversed"),
        margin=dict(l=16, r=60, t=44, b=16),
    ))
    return fig


# ── 5. Daily stacked volume ───────────────────────────────────────────────────
def chart_daily_stacked(df, svc=None):
    data = volume_trends_daily(df, service_name=svc, days_back=30)
    if data.empty:
        return _empty()
    daily = (data.groupby(["day", "service_name"])["log_count"]
             .sum().reset_index().sort_values("day"))
    svcs  = sorted(daily["service_name"].unique())
    fig   = go.Figure()
    for i, s in enumerate(svcs):
        d   = daily[daily["service_name"] == s]
        col = SVC_PALETTE[i % len(SVC_PALETTE)]
        fig.add_trace(go.Scatter(
            x=d["day"], y=d["log_count"],
            name=s.replace("-service", ""),
            mode="lines", stackgroup="one",
            line=dict(width=1, color=col),
            fillcolor=_rgba(col, 0.35),
            hovertemplate=f"<b>{s}</b>  %{{x|%b %d}}  ·  %{{y:,}} logs<extra></extra>",
        ))
    fig.update_layout(**_layout(
        h=280, title="Daily Log Volume by Service  ·  30-Day Stacked",
        xa=dict(tickformat="%b %d", dtick=7 * 86400000),
        lg=dict(orientation="h", yanchor="bottom", y=1.0, xanchor="right", x=1,
                font=dict(size=10)),
    ))
    return fig


# ── 6. Level composition bar ──────────────────────────────────────────────────
def chart_level_stacked(df, svc=None):
    data = level_distribution(df, days_back=30)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    fig = go.Figure()
    for lvl in ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"]:
        s = data[data["level"] == lvl]
        if s.empty:
            continue
        fig.add_trace(go.Bar(
            name=lvl, x=s["service_name"], y=s["log_count"],
            marker=dict(color=LVL_C[lvl], line=dict(width=0)),
            hovertemplate=f"<b>%{{x}}</b>  ·  {lvl}: %{{y:,}}<extra></extra>",
        ))
    fig.update_layout(
        **_layout(h=300, title="Level Composition by Service  ·  30 Days",
                  xa=dict(tickangle=0, tickfont=dict(size=10)),
                  lg=dict(orientation="h", yanchor="bottom", y=1.0,
                          xanchor="right", x=1)),
        barmode="stack", bargap=0.25,
    )
    return fig


# ── 7. Activity heatmap ───────────────────────────────────────────────────────
def chart_heatmap(df, svc=None):
    data = activity_heatmap(df, days_back=30)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    data = data.copy()
    data["day_name"] = data["day_of_week"].map(dict(enumerate(day_names)))
    z = [
        [int(data[(data["day_name"] == day) & (data["hour_of_day"] == h)]["log_count"].sum())
         for h in range(24)]
        for day in day_names
    ]
    fig = go.Figure(go.Heatmap(
        z=z,
        x=[f"{h:02d}:00" for h in range(24)],
        y=day_names,
        colorscale=[
            [0.0,  C["surface"]],
            [0.2,  C["indigo_light"]],
            [0.5,  C["indigo_mid"]],
            [0.8,  C["indigo"]],
            [1.0,  C["indigo_dark"]],
        ],
        hovertemplate="<b>%{y}  %{x}</b><br>%{z:,} events<extra></extra>",
        showscale=True,
        colorbar=dict(
            thickness=8, len=0.85,
            tickfont=dict(size=9, color=C["text_tertiary"], family=MONO),
            bgcolor="rgba(0,0,0,0)",
            bordercolor=C["border"],
            outlinecolor=C["border"],
        ),
        xgap=2, ygap=2,
    ))
    fig.update_layout(**_layout(
        h=270, title="Activity Heatmap  ·  Hour × Weekday (30d)",
        xa=dict(side="bottom",
                tickvals=list(range(0, 24, 3)),
                ticktext=[f"{h:02d}:00" for h in range(0, 24, 3)],
                tickfont=dict(size=9, family=MONO)),
        ya=dict(autorange="reversed", tickfont=dict(size=10)),
    ))
    return fig


# ── 8. Spike detection ────────────────────────────────────────────────────────
def chart_spike_detection(df, svc=None):
    data = error_spike_detection(df)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()

    sc = {"CRITICAL": C["red"], "ELEVATED": C["orange"], "NORMAL": C["green"]}
    bg = {"CRITICAL": C["red_light"], "ELEVATED": C["orange_light"], "NORMAL": C["green_light"]}

    fig = go.Figure()
    fig.add_trace(go.Bar(
        x=data["service_name"], y=[4.0] * len(data),
        marker=dict(color=[bg.get(s, C["surface_alt"]) for s in data["spike_status"]],
                    line=dict(width=0)),
        showlegend=False, hoverinfo="skip",
    ))
    fig.add_trace(go.Bar(
        x=data["service_name"], y=data["spike_ratio"].fillna(0),
        marker=dict(color=[sc.get(s, C["text_muted"]) for s in data["spike_status"]],
                    line=dict(width=0)),
        text=[f"{v:.1f}×" if pd.notna(v) else "N/A" for v in data["spike_ratio"]],
        textposition="outside",
        textfont=dict(size=11, color=C["text_primary"], family=MONO),
        customdata=data[["errors_last_1h", "avg_daily_errors_7d"]].values,
        hovertemplate=(
            "<b>%{x}</b><br>"
            "Spike ratio   : <b>%{y:.2f}×</b> baseline<br>"
            "Errors last 1h: %{customdata[0]}<br>"
            "7d daily avg  : %{customdata[1]:.1f}<extra></extra>"
        ),
    ))
    fig.update_layout(
        **_layout(h=270, title="Error Spike Detection  ·  1h vs 7d Baseline",
                  ya=dict(title=dict(text="× baseline"))),
        barmode="overlay", bargap=0.3,
    )
    for y_val, lbl, col in [(1.5, "Elevated 1.5×", C["orange"]),
                             (3.0, "Critical 3.0×", C["red"])]:
        fig.add_hline(y=y_val, line_dash="dash", line_color=col, line_width=1.2, opacity=0.7,
                      annotation=dict(text=lbl, font=dict(size=9, color=col, family=SANS),
                                      xanchor="right", x=1,
                                      bgcolor="rgba(255,255,255,0.85)", borderpad=3))
    return fig


# ── 9. Silent services ────────────────────────────────────────────────────────
def chart_silent_services(df):
    data = silent_services(df, silent_minutes=10)
    if data.empty:
        fig = go.Figure()
        fig.add_annotation(
            text="✓  All services are active and logging",
            x=0.5, y=0.5, showarrow=False, xref="paper", yref="paper",
            font=dict(color=C["green"], size=13, family=SANS),
        )
        fig.update_layout(**_layout(h=200))
        return fig

    def _c(m):
        if m > 30: return C["red"]
        if m > 15: return C["orange"]
        return C["amber"]

    fig = go.Figure(go.Bar(
        x=data["minutes_silent"], y=data["service_name"],
        orientation="h",
        marker=dict(
            color=[_c(m) for m in data["minutes_silent"]],
            line=dict(width=0),
        ),
        text=[f"{m:.0f} min" for m in data["minutes_silent"]],
        textposition="outside",
        textfont=dict(size=11, color=C["text_primary"], family=MONO),
        hovertemplate=(
            "<b>%{y}</b><br>"
            "Silent for: <b>%{x:.1f} min</b><br>"
            "Last seen : %{customdata}<extra></extra>"
        ),
        customdata=data["last_log_at"].dt.strftime("%Y-%m-%d %H:%M"),
    ))
    fig.update_layout(**_layout(
        h=max(200, 50 + 40 * len(data)),
        title="Silent Services  ·  No Logs > 10 Minutes",
        xa=dict(title=dict(text="minutes silent")),
        ya=dict(tickfont=dict(size=11, color=C["text_primary"], family=SANS)),
    ))
    fig.add_vline(x=30, line_dash="dash", line_color=C["red"], line_width=1.2, opacity=0.6,
                  annotation=dict(text="30 min threshold",
                                  font=dict(size=9, color=C["red"], family=SANS),
                                  bgcolor="rgba(255,255,255,0.85)", borderpad=3))
    return fig


# ── 10. Log volume ranking ────────────────────────────────────────────────────
def chart_top_noisy(df, svc=None):
    data = top_noisy_services(df)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    fig = go.Figure(go.Bar(
        x=data["service_name"],
        y=data["total_logs"],
        marker=dict(
            color=SVC_PALETTE[:len(data)],
            line=dict(width=0),
        ),
        text=[f"{p:.1f}%" for p in data["pct_of_total"]],
        textposition="outside",
        textfont=dict(size=10, color=C["text_secondary"], family=MONO),
        customdata=data[["pct_of_total"]].values,
        hovertemplate=(
            "<b>%{x}</b><br>"
            "Log volume : <b>%{y:,}</b><br>"
            "% of total : %{customdata[0]:.1f}%<extra></extra>"
        ),
    ))
    fig.update_layout(**_layout(
        h=270, title="Log Volume Ranking by Service  ·  24h",
        ya=dict(title=dict(text="log count"), showgrid=True),
    ))
    return fig


# ── 11. Warn rate ─────────────────────────────────────────────────────────────
def chart_warn_rate(df, svc=None):
    data = warn_rate_24h(df).sort_values("warn_rate_percent", ascending=False)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    colors = [C["orange"] if r > 10 else (C["amber"] if r > 5 else C["green"])
              for r in data["warn_rate_percent"]]
    fig = go.Figure(go.Bar(
        x=data["service_name"], y=data["warn_rate_percent"],
        marker=dict(color=colors, line=dict(width=0)),
        text=[f"{r:.1f}%" for r in data["warn_rate_percent"]],
        textposition="outside",
        textfont=dict(size=10, color=C["text_secondary"], family=MONO),
        customdata=data[["warn_count", "total_logs"]].values,
        hovertemplate=(
            "<b>%{x}</b><br>"
            "Warn rate : <b>%{y:.2f}%</b><br>"
            "Warnings / Total : %{customdata[0]:,} / %{customdata[1]:,}<extra></extra>"
        ),
    ))
    fig.update_layout(**_layout(
        h=265, title="Warning Rate by Service  ·  24h Leading Indicator",
        ya=dict(title=dict(text="warn %")),
    ))
    fig.add_hline(y=10, line_dash="dash", line_color=C["orange"], line_width=1.2, opacity=0.6,
                  annotation=dict(text="Watch threshold 10%",
                                  font=dict(size=9, color=C["orange"], family=SANS),
                                  xanchor="right", x=1,
                                  bgcolor="rgba(255,255,255,0.85)", borderpad=3))
    return fig


# ── 12. MTBE ──────────────────────────────────────────────────────────────────
def chart_mtbe(df, svc=None):
    data = mean_time_between_errors(df, days_back=7).sort_values(
        "avg_minutes_between_errors", ascending=True)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    max_v = data["avg_minutes_between_errors"].max()

    def _c(v):
        r = v / max(max_v, 1)
        return C["green"] if r > 0.6 else (C["amber"] if r > 0.3 else C["orange"])

    fig = go.Figure(go.Bar(
        x=data["avg_minutes_between_errors"],
        y=data["service_name"],
        orientation="h",
        marker=dict(color=[_c(v) for v in data["avg_minutes_between_errors"]],
                    line=dict(width=0)),
        text=[f"{v:.0f} min" for v in data["avg_minutes_between_errors"]],
        textposition="outside",
        textfont=dict(size=11, color=C["text_primary"], family=MONO),
        customdata=data[["total_errors"]].values,
        hovertemplate=(
            "<b>%{y}</b><br>"
            "Avg between errors : <b>%{x:.1f} min</b><br>"
            "Total errors (7d)  : %{customdata[0]:,}<extra></extra>"
        ),
    ))
    fig.update_layout(**_layout(
        h=270, title="Mean Time Between Errors  ·  7d (Higher = More Stable)",
        xa=dict(title=dict(text="minutes between errors")),
        ya=dict(tickfont=dict(size=11, color=C["text_primary"], family=SANS)),
    ))
    return fig


# ═══════════════════════════════════════════════════════════════════════════════
#  UI COMPONENTS
# ═══════════════════════════════════════════════════════════════════════════════

def _kpi_card(label, value, subtitle, accent_color, tag=None, tag_color=None):
    """Single KPI tile."""
    return html.Div([
        # Top row: label + tag
        html.Div([
            html.Span(label, style={
                "fontSize": "11px", "color": C["text_tertiary"],
                "fontFamily": SANS, "fontWeight": "500",
                "letterSpacing": "0.3px", "textTransform": "uppercase",
            }),
            html.Span(tag, style={
                "fontSize": "10px", "color": tag_color or C["text_tertiary"],
                "fontFamily": MONO, "fontWeight": "600",
                "marginLeft": "8px", "padding": "1px 6px",
                "background": _rgba(tag_color or C["text_tertiary"], 0.1),
                "borderRadius": "3px",
            }) if tag else None,
        ], style={"display": "flex", "alignItems": "center", "marginBottom": "8px"}),
        # Value
        html.Div(str(value), style={
            "fontSize": "32px", "fontWeight": "700", "color": accent_color,
            "lineHeight": "1", "fontFamily": MONO, "letterSpacing": "-1px",
        }),
        # Subtitle
        html.Div(subtitle, style={
            "fontSize": "11px", "color": C["text_tertiary"],
            "fontFamily": SANS, "marginTop": "6px",
        }),
        # Bottom accent line
        html.Div(style={
            "position": "absolute", "bottom": "0", "left": "0", "right": "0",
            "height": "2px",
            "background": f"linear-gradient(90deg, {accent_color}, transparent)",
            "borderRadius": "0 0 6px 6px",
        }),
    ], style={
        "background": C["surface"],
        "border": f"1px solid {C['border']}",
        "borderRadius": "6px",
        "padding": "16px 20px 14px",
        "flex": "1", "minWidth": "150px",
        "position": "relative",
        "boxShadow": "0 1px 3px rgba(15,23,41,0.06)",
    })


def _status_pill(status):
    cfg = {
        "HEALTHY":  (C["green"],  C["green_light"],  "●"),
        "DEGRADED": (C["orange"], C["orange_light"],  "●"),
        "CRITICAL": (C["red"],    C["red_light"],     "●"),
        "SILENT":   (C["text_tertiary"], C["surface_alt"], "○"),
    }
    color, bg, dot = cfg.get(status, (C["text_tertiary"], C["surface_alt"], "○"))
    return html.Span([
        html.Span(dot + " ", style={"fontSize": "8px", "color": color}),
        html.Span(status),
    ], style={
        "color": color, "background": bg,
        "fontSize": "10px", "fontWeight": "600", "letterSpacing": "0.5px",
        "fontFamily": MONO, "padding": "2px 8px", "borderRadius": "4px",
        "border": f"1px solid {_rgba(color, 0.2)}",
    })


def _health_table(df):
    data = service_health_summary(df)
    if data.empty:
        return html.Div("No data available", style={"color": C["text_tertiary"],
                                                     "padding": "20px", "fontSize": "13px"})

    def th(t, left=False):
        return html.Th(t, style={
            "padding": "10px 16px", "fontSize": "10px", "color": C["text_tertiary"],
            "textTransform": "uppercase", "letterSpacing": "0.8px",
            "borderBottom": f"2px solid {C['border']}",
            "fontFamily": SANS, "fontWeight": "600",
            "textAlign": "left" if left else "right",
            "whiteSpace": "nowrap",
            "background": C["surface_alt"],
        })

    header = html.Tr([
        th("Service", left=True), th("Status", left=True),
        th("Logs 24h"), th("Errors"), th("Warnings"),
        th("Error %"), th("Warn %"), th("Err /1h"), th("Last Seen"),
    ])

    rows = []
    for idx, r in data.iterrows():
        bar_pct = min(r["error_rate_24h"] * 5, 100)
        bar_col = (C["red"] if r["error_rate_24h"] > 10
                   else C["orange"] if r["error_rate_24h"] > 5 else C["green"])

        bg = C["surface_alt"] if idx % 2 == 0 else C["surface"]

        def td(val, right=True, bold=False, color=None):
            return html.Td(val, style={
                "padding": "9px 16px", "fontSize": "12px",
                "fontFamily": MONO if right else SANS,
                "color": color or (C["text_primary"] if bold else C["text_secondary"]),
                "textAlign": "right" if right else "left",
                "whiteSpace": "nowrap",
                "background": bg,
            })

        rows.append(html.Tr([
            td(r["service_name"].replace("-service", ""), right=False, bold=True),
            html.Td(_status_pill(r["status"]),
                    style={"padding": "9px 16px", "background": bg}),
            td(f"{r['total_logs_24h']:,}"),
            td(f"{r['errors_last_1h']:,}",
               color=C["red"] if r["errors_last_1h"] > 0 else C["text_tertiary"]),
            td(f"{int(r['total_logs_24h'] * r['warn_rate_24h'] / 100):,}",
               color=C["orange"] if r["warn_rate_24h"] > 5 else C["text_tertiary"]),
            html.Td(html.Div([
                html.Div(style={
                    "width": f"{bar_pct}%", "minWidth": "3px", "height": "4px",
                    "background": bar_col, "borderRadius": "2px", "marginBottom": "4px",
                }),
                html.Span(f"{r['error_rate_24h']:.1f}%", style={
                    "fontSize": "11px", "color": bar_col,
                    "fontFamily": MONO, "fontWeight": "600",
                }),
            ], style={"padding": "9px 16px", "minWidth": "80px", "background": bg})),
            td(f"{r['warn_rate_24h']:.1f}%",
               color=C["orange"] if r["warn_rate_24h"] > 10 else C["text_secondary"]),
            td(f"{r['errors_last_1h']}",
               color=C["red"] if r["errors_last_1h"] > 10 else C["text_secondary"]),
            td(r["last_log_at"].strftime("%H:%M:%S") if pd.notna(r["last_log_at"]) else "—",
               color=C["text_tertiary"]),
        ], style={"borderBottom": f"1px solid {C['border']}"}))

    return html.Div(
        html.Table([html.Thead(header), html.Tbody(rows)],
                   style={"width": "100%", "borderCollapse": "collapse"}),
        style={"overflowX": "auto", "borderRadius": "0 0 6px 6px"},
    )


def _error_feed(df):
    data = recent_critical_events(df, limit=25)
    if data.empty:
        return html.Div(
            "✓  No recent errors found in dataset",
            style={"color": C["green"], "padding": "20px", "fontSize": "13px",
                   "fontFamily": SANS}
        )
    items = []
    for i, (_, row) in enumerate(data.iterrows()):
        svc_short = row["service_name"].replace("-service", "").upper()
        ts_str    = row["timestamp"].strftime("%b %d  %H:%M:%S")
        border_l  = C["red"] if i < 5 else C["border_mid"]
        items.append(html.Div([
            html.Div([
                html.Span(ts_str, style={
                    "fontSize": "10px", "color": C["text_tertiary"],
                    "fontFamily": MONO, "marginRight": "10px",
                }),
                html.Span(svc_short, style={
                    "fontSize": "9px", "color": C["indigo"],
                    "fontFamily": MONO, "fontWeight": "700",
                    "background": C["indigo_light"],
                    "padding": "1px 7px", "borderRadius": "3px",
                    "letterSpacing": "0.8px",
                    "border": f"1px solid {C['indigo_mid']}",
                }),
            ], style={"marginBottom": "4px", "display": "flex", "alignItems": "center"}),
            html.Div(row["message"], style={
                "fontSize": "12px", "color": C["text_secondary"],
                "fontFamily": SANS, "lineHeight": "1.5",
            }),
        ], style={
            "padding": "10px 16px",
            "borderBottom": f"1px solid {C['border']}",
            "borderLeft": f"3px solid {border_l}",
        }))
    return html.Div(items, style={"overflowY": "auto", "maxHeight": "380px"})


def _panel(children, col_span=1, title=None):
    return html.Div([
        html.Div([
            html.Span(title, style={
                "fontSize": "11px", "fontWeight": "600", "color": C["text_secondary"],
                "fontFamily": SANS, "letterSpacing": "0.2px",
            })
        ], style={
            "padding": "12px 18px 10px",
            "borderBottom": f"1px solid {C['border']}",
        }) if title else None,
        html.Div(children, style={"padding": "4px 4px 4px"}),
    ], style={
        "background": C["surface"],
        "border": f"1px solid {C['border']}",
        "borderRadius": "6px",
        "gridColumn": f"span {col_span}",
        "overflow": "hidden",
        "boxShadow": "0 1px 3px rgba(15,23,41,0.05)",
    })


def _section_header(title, subtitle=None):
    return html.Div([
        html.Div([
            html.Span(title, style={
                "fontSize": "13px", "fontWeight": "700", "color": C["text_primary"],
                "fontFamily": SANS, "marginRight": "10px",
            }),
            html.Span(subtitle, style={
                "fontSize": "11px", "color": C["text_tertiary"],
                "fontFamily": SANS,
            }) if subtitle else None,
        ], style={"display": "flex", "alignItems": "center"}),
        html.Div(style={
            "marginTop": "8px", "height": "1px", "background": C["border"],
        }),
    ], style={"marginBottom": "14px"})


def _section(title, subtitle, children):
    return html.Div([
        _section_header(title, subtitle),
        children,
    ], style={"marginBottom": "32px"})


# ═══════════════════════════════════════════════════════════════════════════════
#  APP ASSEMBLY
# ═══════════════════════════════════════════════════════════════════════════════

FONTS = (
    "https://fonts.googleapis.com/css2?"
    "family=DM+Sans:wght@300;400;500;600;700&"
    "family=JetBrains+Mono:wght@400;500;600;700&"
    "display=swap"
)


def build_app(df: pd.DataFrame) -> Dash:
    app  = Dash(__name__, title="LogStream · Observability")
    kpis = _kpis(df)
    cfg  = {"displayModeBar": False, "responsive": True}

    svc_options = [{"label": "All Services", "value": "__all__"}] + [
        {"label": s.replace("-service", "").capitalize(), "value": s}
        for s in sorted(df["service_name"].unique())
    ]

    app.index_string = f"""<!DOCTYPE html>
<html>
<head>
    {{%metas%}}
    <title>{{%title%}}</title>
    {{%favicon%}}
    {{%css%}}
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link rel="stylesheet" href="{FONTS}">
    <style>
        *, *::before, *::after {{ box-sizing: border-box; }}
        html, body {{
            margin: 0; padding: 0;
            background: {C['page']};
            color: {C['text_primary']};
            -webkit-font-smoothing: antialiased;
        }}
        ::-webkit-scrollbar {{ width: 5px; height: 5px; }}
        ::-webkit-scrollbar-track {{ background: {C['page']}; }}
        ::-webkit-scrollbar-thumb {{ background: {C['border_mid']}; border-radius: 3px; }}
        ::-webkit-scrollbar-thumb:hover {{ background: {C['text_muted']}; }}
        /* Dropdown overrides */
        .Select-control {{
            background: {C['surface']} !important;
            border: 1px solid {C['border']} !important;
            border-radius: 5px !important;
        }}
        .Select-menu-outer {{
            background: {C['surface']} !important;
            border: 1px solid {C['border']} !important;
            box-shadow: 0 4px 12px rgba(15,23,41,0.12) !important;
        }}
        .Select-option {{
            background: {C['surface']} !important;
            color: {C['text_secondary']} !important;
            font-family: {SANS};
            font-size: 12px;
        }}
        .Select-option.is-focused {{
            background: {C['indigo_light']} !important;
            color: {C['indigo_dark']} !important;
        }}
        .Select-value-label {{
            color: {C['text_primary']} !important;
            font-family: {SANS};
            font-size: 12px !important;
        }}
        .Select-placeholder {{
            color: {C['text_tertiary']} !important;
            font-family: {SANS};
            font-size: 12px;
        }}
        .Select-arrow {{ border-top-color: {C['text_tertiary']} !important; }}
    </style>
</head>
<body>
    {{%app_entry%}}
    <footer>{{%config%}}{{%scripts%}}{{%renderer%}}</footer>
</body>
</html>"""

    app.layout = html.Div([

        # ══ HEADER ══════════════════════════════════════════════════════════
        html.Div([
            # Left: brand
            html.Div([
                html.Div(style={
                    "width": "8px", "height": "8px", "borderRadius": "2px",
                    "background": C["indigo"], "marginRight": "10px", "flexShrink": "0",
                }),
                html.Span("LogStream", style={
                    "fontFamily": SANS, "fontWeight": "700",
                    "fontSize": "15px", "color": C["text_primary"],
                    "letterSpacing": "-0.3px",
                }),
                html.Span(" Observability", style={
                    "fontFamily": SANS, "fontSize": "13px",
                    "color": C["text_tertiary"], "marginLeft": "4px",
                }),
            ], style={"display": "flex", "alignItems": "center"}),

            # Centre: service filter
            html.Div([
                html.Span("Service", style={
                    "fontSize": "11px", "color": C["text_tertiary"],
                    "fontFamily": SANS, "marginRight": "8px", "whiteSpace": "nowrap",
                }),
                dcc.Dropdown(
                    id="svc-filter",
                    options=svc_options,
                    value="__all__",
                    clearable=False,
                    searchable=False,
                    style={"width": "200px"},
                ),
            ], style={"display": "flex", "alignItems": "center"}),

            # Right: data range + clock
            html.Div([
                html.Div([
                    html.Span("Data range  ", style={
                        "fontSize": "10px", "color": C["text_muted"],
                        "fontFamily": SANS,
                    }),
                    html.Span(
                        f"{df['timestamp'].min().strftime('%b %d')}  –  "
                        f"{df['timestamp'].max().strftime('%b %d, %Y')}",
                        style={"fontSize": "11px", "color": C["text_secondary"],
                               "fontFamily": MONO},
                    ),
                ], style={"marginBottom": "2px", "textAlign": "right"}),
                html.Div(id="live-clock", style={
                    "fontSize": "11px", "color": C["text_tertiary"],
                    "fontFamily": MONO, "textAlign": "right",
                }),
                dcc.Interval(id="clock-tick", interval=1000, n_intervals=0),
                dcc.Interval(id="data-refresh", interval=60_000,     n_intervals=0),
            ]),
        ], style={
            "display": "flex", "justifyContent": "space-between",
            "alignItems": "center", "padding": "12px 28px",
            "borderBottom": f"1px solid {C['border']}",
            "background": C["header_bg"],
            "position": "sticky", "top": "0", "zIndex": "200",
            "boxShadow": "0 1px 0 rgba(15,23,41,0.06)",
        }),

        # ══ MAIN CONTENT ════════════════════════════════════════════════════
        html.Div([

            # ── KPI ROW ──────────────────────────────────────────────────
            _section("Snapshot", "Last 24 Hours",
                html.Div([
                    _kpi_card("Total Events",  f"{kpis['total']:,}",
                              f"~{kpis['throughput']} events/hr average",
                              C["text_primary"]),
                    _kpi_card("Errors",  f"{kpis['errors']:,}",
                              "Level = ERROR",
                              C["red"], tag=kpis["trend"], tag_color=kpis["trend_color"]),
                    _kpi_card("Warnings", f"{kpis['warns']:,}",
                              "Level = WARN · leading indicator",
                              C["orange"]),
                    _kpi_card("Error Rate", f"{kpis['erate']}%",
                              "Errors / Total  ·  SLO < 5%",
                              C["red"] if kpis["erate"] > 10
                              else C["orange"] if kpis["erate"] > 5 else C["green"]),
                    _kpi_card("Services", f"{kpis['services']}",
                              f"Monitored  ·  {len(df):,} total records",
                              C["indigo"]),
                ], style={"display": "flex", "gap": "12px", "flexWrap": "wrap"}),
            ),

            # ── TRAFFIC SECTION ──────────────────────────────────────────
            _section("Traffic", "Volume & Composition",
                html.Div([
                    # Timeline (wide) + Donut (narrow)
                    html.Div([
                        _panel(dcc.Graph(id="g-timeline", config=cfg), col_span=2),
                        _panel(dcc.Graph(id="g-donut",    config=cfg), col_span=1),
                    ], style={"display": "grid", "gridTemplateColumns": "2fr 1fr",
                              "gap": "12px", "marginBottom": "12px"}),
                    _panel(dcc.Graph(id="g-daily", config=cfg)),
                ]),
            ),

            # ── RELIABILITY SECTION ──────────────────────────────────────
            _section("Reliability", "Error Analysis",
                html.Div([
                    html.Div([
                        _panel(dcc.Graph(id="g-errrate", config=cfg)),
                        _panel(dcc.Graph(id="g-toperrs", config=cfg)),
                    ], style={"display": "grid", "gridTemplateColumns": "1fr 1fr",
                              "gap": "12px"}),
                ]),
            ),

            # ── SERVICE HEALTH TABLE ─────────────────────────────────────
            _section("Service Health", "24-Hour Window",
                html.Div(
                    id="health-table",
                    style={
                        "background": C["surface"],
                        "border": f"1px solid {C['border']}",
                        "borderRadius": "6px",
                        "overflow": "hidden",
                        "boxShadow": "0 1px 3px rgba(15,23,41,0.05)",
                    },
                ),
            ),

            # ── PATTERNS SECTION ─────────────────────────────────────────
            _section("Patterns", "Behavioral Analysis",
                html.Div([
                    html.Div([
                        _panel(dcc.Graph(id="g-heatmap",  config=cfg)),
                        _panel(dcc.Graph(id="g-levelbar", config=cfg)),
                    ], style={"display": "grid", "gridTemplateColumns": "1fr 1fr",
                              "gap": "12px"}),
                ]),
            ),

            # ── SIGNALS SECTION ──────────────────────────────────────────
            _section("Signals", "Anomaly Detection & Reliability",
                html.Div([
                    html.Div([
                        _panel(dcc.Graph(id="g-noisy", config=cfg)),
                        _panel(dcc.Graph(id="g-spike", config=cfg)),
                    ], style={"display": "grid", "gridTemplateColumns": "1fr 1fr",
                              "gap": "12px", "marginBottom": "12px"}),
                    html.Div([
                        _panel(dcc.Graph(id="g-warn", config=cfg)),
                        _panel(dcc.Graph(id="g-mtbe", config=cfg)),
                    ], style={"display": "grid", "gridTemplateColumns": "1fr 1fr",
                              "gap": "12px", "marginBottom": "12px"}),
                    _panel(dcc.Graph(id="g-silent", config=cfg)),
                ]),
            ),

            # ── LIVE ERROR FEED ──────────────────────────────────────────
            _section(
                html.Span([
                    html.Span("● ", style={"color": C["red"], "fontSize": "10px"}),
                    "Live Error Feed",
                ]),
                "Most Recent 25 Errors",
                html.Div(
                    id="error-feed",
                    style={
                        "background": C["surface"],
                        "border": f"1px solid {C['border']}",
                        "borderLeft": f"3px solid {C['red']}",
                        "borderRadius": "0 6px 6px 0",
                        "overflow": "hidden",
                        "boxShadow": "0 1px 3px rgba(15,23,41,0.05)",
                    },
                ),
            ),

            # ── FOOTER ───────────────────────────────────────────────────
            html.Div([
                html.Div(style={"height": "1px", "background": C["border"], "marginBottom": "16px"}),
                html.Div([
                    html.Span("LogStream Observability Platform", style={
                        "color": C["indigo"], "fontWeight": "600",
                        "fontSize": "11px", "fontFamily": SANS,
                    }),
                    html.Span(
                        f"  ·  {len(df):,} records  ·  "
                        f"{df['service_name'].nunique()} services  ·  Team 13  ·  "
                        f"Data Engineering",
                        style={"color": C["text_tertiary"], "fontSize": "11px",
                               "fontFamily": SANS},
                    ),
                ], style={"textAlign": "center"}),
            ], style={"padding": "0 0 24px"}),

        ], style={"padding": "24px 28px", "maxWidth": "1600px", "margin": "0 auto"}),

    ], style={
        "background": C["page"],
        "minHeight": "100vh",
        "fontFamily": SANS,
        "color": C["text_primary"],
    })

    # ── CALLBACKS ─────────────────────────────────────────────────────────────

    @app.callback(Output("live-clock", "children"),
                  Input("clock-tick", "n_intervals"))
    def _clock(n):
        return datetime.now(timezone.utc).strftime("UTC  %Y-%m-%d  %H:%M:%S")

    def _svc(val):
        return None if val == "__all__" else val

    @app.callback(
        Output("g-timeline",   "figure"),
        Output("g-donut",      "figure"),
        Output("g-daily",      "figure"),
        Output("g-errrate",    "figure"),
        Output("g-toperrs",    "figure"),
        Output("g-heatmap",    "figure"),
        Output("g-levelbar",   "figure"),
        Output("g-noisy",      "figure"),
        Output("g-spike",      "figure"),
        Output("g-warn",       "figure"),
        Output("g-mtbe",       "figure"),
        Output("g-silent",     "figure"),
        Output("health-table", "children"),
        Output("error-feed",   "children"),
        Input("svc-filter",   "value"),
        Input("data-refresh", "n_intervals"),
    )
    def update_all(svc_val,_refresh):
        df = load_df_postgres()   
        s = _svc(svc_val)
        return (
            chart_volume_timeline(df, svc=s),
            chart_level_donut(df, svc=s),
            chart_daily_stacked(df, svc=s),
            chart_error_rate(df, svc=s),
            chart_top_errors(df, svc=s),
            chart_heatmap(df, svc=s),
            chart_level_stacked(df, svc=s),
            chart_top_noisy(df, svc=s),
            chart_spike_detection(df, svc=s),
            chart_warn_rate(df, svc=s),
            chart_mtbe(df, svc=s),
            chart_silent_services(df),
            _health_table(df),
            _error_feed(df),
        )

    return app


# ═══════════════════════════════════════════════════════════════════════════════
#  ENTRY POINT
# ═══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    """     parser = argparse.ArgumentParser(description="LogStream Observability Dashboard")
    parser.add_argument("--data", default=None, help=".json or .csv log file path")
    parser.add_argument("--port", type=int, default=8050)
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args()

    df = load_df(args.data)
    print(f"\n[logstream] Records  : {len(df):,}")
    print(f"[logstream] Range    : {df['timestamp'].min().strftime('%Y-%m-%d')}  →  "
          f"{df['timestamp'].max().strftime('%Y-%m-%d')}")
    print(f"[logstream] Services : {', '.join(sorted(df['service_name'].unique()))}")
    err_n = int((df['level'] == 'ERROR').sum())
    print(f"[logstream] Errors   : {err_n:,}  ({err_n/len(df)*100:.1f}%)")
    print(f"\n[logstream] →  http://{args.host}:{args.port}\n")

    app = build_app(df)
    app.run(debug=False, host=args.host, port=args.port) 
    """
    
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8050)
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args()

    print("[logstream] Connecting to Postgres...")
    df = load_df_postgres() 
    print(f"[logstream] Loaded {len(df):,} records")

    app = build_app(df)
    app.run(debug=False, host=args.host, port=args.port)