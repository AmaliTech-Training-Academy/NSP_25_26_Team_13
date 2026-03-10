"""
dashboard.py  ·  LogStream Observability Platform
==================================================
Professional analytics dashboard — Plotly Dash.

All 13 analytics functions from data_analytics.py are visualised.
Service filter dropdown applies to all time-sensitive charts.

Design: Monochrome steel base with surgical amber + semantic accents.
        "Network operations centre at 3am" aesthetic.
        IBM Plex Mono data · Space Grotesk labels.

Run (from data-engineering/ root):
    python scripts/dashboard.py --data data/logs_YYYYMMDD_HHMMSS.json
    python scripts/dashboard.py                         # uses generated sample data
    http://127.0.0.1:8050
"""

# ── Path bootstrap (run from any working directory) ───────────────────────────
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import argparse
import random
import uuid
from datetime import datetime, timedelta, timezone

import pandas as pd
import plotly.graph_objects as go
from dash import Dash, dcc, html, Input, Output, callback

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
# DESIGN SYSTEM
# ═══════════════════════════════════════════════════════════════════════════════

C = {
    # ── Backgrounds — 4-layer depth ──────────────────────────────────────────
    "bg_base":       "#080A0D",   # page / outermost
    "bg_raised":     "#0F1218",   # cards
    "bg_float":      "#161B23",   # elevated surfaces
    "bg_inset":      "#0C0F14",   # chart areas, table cells
    "bg_stripe":     "#0D1117",   # alternating table rows

    # ── Primary accent — amber (terminal cursor / alert) ──────────────────
    "amber":         "#F0A500",
    "amber_dim":     "#7A5200",
    "amber_glow":    "rgba(240,165,0,0.10)",
    "amber_subtle":  "rgba(240,165,0,0.05)",

    # ── Semantic colours ─────────────────────────────────────────────────
    "red":           "#E8354A",
    "red_dim":       "rgba(232,53,74,0.13)",
    "red_subtle":    "rgba(232,53,74,0.06)",
    "orange":        "#F07030",
    "orange_dim":    "rgba(240,112,48,0.15)",
    "green":         "#28C478",
    "green_dim":     "rgba(40,196,120,0.12)",
    "blue":          "#3D8FE8",
    "blue_dim":      "rgba(61,143,232,0.15)",
    "purple":        "#9060E8",
    "teal":          "#20B8CC",

    # ── Type scale ────────────────────────────────────────────────────────
    "text_hi":       "#D8DDE8",
    "text_mid":      "#7A8494",
    "text_lo":       "#383E4A",
    "text_ghost":    "#232830",

    # ── Structure ─────────────────────────────────────────────────────────
    "rule":          "#161B23",
    "rule_hi":       "#222830",
    "rule_bright":   "#2D3440",
}

# Log level → colour
LVL = {
    "ERROR": C["red"],
    "WARN":  C["orange"],
    "INFO":  C["blue"],
    "DEBUG": C["purple"],
    "TRACE": C["text_lo"],
}

# ── Plotly base layout components ─────────────────────────────────────────────
_PL = dict(
    paper_bgcolor="rgba(0,0,0,0)",
    plot_bgcolor="rgba(0,0,0,0)",
    font=dict(family="'IBM Plex Mono', 'Courier New', monospace",
              color=C["text_mid"], size=11),
    margin=dict(l=12, r=16, t=40, b=12),
    hoverlabel=dict(
        bgcolor=C["bg_float"],
        bordercolor=C["rule_bright"],
        font=dict(family="'IBM Plex Mono', monospace", size=11, color=C["text_hi"]),
    ),
)
_XA = dict(
    gridcolor=C["rule"], linecolor=C["rule_hi"], zeroline=False,
    tickfont=dict(size=9, color=C["text_lo"]),
    title_font=dict(size=10, color=C["text_mid"]),
)
_YA = dict(
    gridcolor=C["rule"], linecolor=C["rule_hi"], zeroline=False,
    tickfont=dict(size=9, color=C["text_lo"]),
    title_font=dict(size=10, color=C["text_mid"]),
)
_LG = dict(
    bgcolor="rgba(15,18,24,0.95)", bordercolor=C["rule_hi"],
    borderwidth=1, font=dict(size=10, color=C["text_mid"]),
)


def _L(h=280, title="", xa=None, ya=None, lg=None, **kw):
    """Compose a full Plotly layout. No key collisions."""
    return {
        **_PL, "height": h,
        "title": dict(
            text=title,
            font=dict(size=11, color=C["text_hi"],
                      family="'IBM Plex Mono', monospace"),
            x=0.0, pad=dict(l=0, b=8),
        ),
        "xaxis":  {**_XA, **(xa or {})},
        "yaxis":  {**_YA, **(ya or {})},
        "legend": {**_LG, **(lg or {})},
        **kw,
    }


def _hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def _tag(text):
    return (
        f"<span style='font-size:8px;color:{C['text_lo']};letter-spacing:1.5px;"
        f"font-family:IBM Plex Mono,monospace'> · {text.upper()}</span>"
    )


def _empty(msg="NO DATA IN WINDOW", h=240):
    fig = go.Figure()
    fig.add_annotation(
        text=msg, x=0.5, y=0.5, showarrow=False, xref="paper", yref="paper",
        font=dict(color=C["text_lo"], size=10, family="IBM Plex Mono, monospace"),
    )
    fig.update_layout(**_L(h=h))
    return fig


# ═══════════════════════════════════════════════════════════════════════════════
# SAMPLE DATA GENERATOR  (used when no --data file is supplied)
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


# ═══════════════════════════════════════════════════════════════════════════════
# DATA LOADER
# ═══════════════════════════════════════════════════════════════════════════════

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


# ═══════════════════════════════════════════════════════════════════════════════
# KPI HELPERS
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
    trend  = "▲" if errors > e_prev else ("▼" if errors < e_prev else "─")
    tc     = C["red"] if errors > e_prev else (C["green"] if errors < e_prev else C["text_lo"])
    return dict(
        total=total, errors=errors, warns=warns, erate=erate,
        services=df["service_name"].nunique(),
        throughput=round(total / 24, 1),
        trend=trend, trend_color=tc,
    )


# ═══════════════════════════════════════════════════════════════════════════════
# CHART BUILDERS
# ═══════════════════════════════════════════════════════════════════════════════

# ── 1. Hourly volume timeline  (vw_volume_trends_hourly) ─────────────────────
def chart_volume_timeline(df, svc=None):
    data = volume_trends_hourly(df, service_name=svc, days_back=7)
    if data.empty:
        return _empty()
    fig = go.Figure()
    for lvl, opacity in [("TRACE", 0.25), ("DEBUG", 0.35), ("INFO", 0.5)]:
        s = data[data["level"] == lvl]
        if s.empty:
            continue
        r, g, b = _hex_to_rgb(LVL[lvl])
        fig.add_trace(go.Scatter(
            x=s["hour"], y=s["log_count"], name=lvl,
            mode="lines", stackgroup="bg", line=dict(width=0),
            fillcolor=f"rgba({r},{g},{b},{opacity})",
            hovertemplate=f"<b>{lvl}</b>  %{{x|%a %d %H:00}}  —  %{{y:,}}<extra></extra>",
        ))
    s = data[data["level"] == "WARN"]
    if not s.empty:
        fig.add_trace(go.Scatter(
            x=s["hour"], y=s["log_count"], name="WARN", mode="lines",
            line=dict(color=C["orange"], width=1.5, dash="dot"),
            hovertemplate="<b>WARN</b>  %{x|%a %d %H:00}  —  %{y:,}<extra></extra>",
        ))
    s = data[data["level"] == "ERROR"]
    if not s.empty:
        fig.add_trace(go.Scatter(
            x=s["hour"], y=s["log_count"], name="ERROR", mode="lines",
            line=dict(color=C["red"], width=2),
            fill="tozeroy", fillcolor=C["red_dim"],
            hovertemplate="<b>ERROR</b>  %{x|%a %d %H:00}  —  %{y:,}<extra></extra>",
        ))
    fig.update_layout(**_L(
        h=300, title=f"Log Traffic{_tag('7d · hourly')}",
        xa=dict(tickformat="%a %d", dtick=86400000),
        lg=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1,
                font=dict(size=9)),
    ))
    return fig


# ── 2. Error rate per service  (vw_error_rate_24h) ───────────────────────────
def chart_error_rate(df, svc=None):
    data = error_rate_24h(df, service_name=svc).sort_values("error_rate_percent")
    if data.empty:
        return _empty()

    def _c(r):
        if r >= 15: return C["red"]
        if r >= 8:  return C["orange"]
        if r >= 3:  return C["amber"]
        return C["green"]

    fig = go.Figure()
    fig.add_trace(go.Bar(
        x=[100] * len(data), y=data["service_name"],
        orientation="h",
        marker=dict(color=C["bg_inset"], line=dict(width=0)),
        showlegend=False, hoverinfo="skip",
    ))
    fig.add_trace(go.Bar(
        x=data["error_rate_percent"], y=data["service_name"],
        orientation="h",
        marker=dict(color=[_c(r) for r in data["error_rate_percent"]],
                    line=dict(width=0)),
        text=[f"{r:.1f}%" for r in data["error_rate_percent"]],
        textposition="outside",
        textfont=dict(size=10, color=C["text_hi"]),
        customdata=data[["total_logs", "error_count"]].values,
        hovertemplate=(
            "<b>%{y}</b><br>"
            "Error rate : <b>%{x:.2f}%</b><br>"
            "Errors     : %{customdata[1]:,} / %{customdata[0]:,}<extra></extra>"
        ),
    ))
    fig.update_layout(
        **_L(h=300, title=f"Error Rate by Service{_tag('24h')}",
             xa=dict(range=[0, 130], showgrid=False, showticklabels=False),
             ya=dict(tickfont=dict(size=10, color=C["text_hi"]))),
        barmode="overlay", bargap=0.35,
    )
    for x_val, lbl, col in [(5, "SLO 5%", C["amber"]), (10, "CRIT 10%", C["red"])]:
        fig.add_vline(x=x_val, line_dash="dash", line_color=col,
                      line_width=1, opacity=0.5,
                      annotation=dict(text=lbl, font=dict(size=8, color=col),
                                      yanchor="bottom"))
    return fig


# ── 3. Level donut  (vw_level_distribution snapshot) ────────────────────────
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
        hole=0.72,
        marker=dict(
            colors=[LVL.get(l, C["text_lo"]) for l in counts["level"]],
            line=dict(color=C["bg_base"], width=3),
        ),
        textinfo="none",
        hovertemplate="<b>%{label}</b>  %{value:,}  (%{percent})<extra></extra>",
        sort=False,
    ))
    fig.update_layout(
        **_L(h=260, title=f"Level Mix{_tag('24h')}",
             lg=dict(orientation="v", x=1.02, font=dict(size=9))),
        annotations=[
            dict(text=f"<b>{erate}%</b>", x=0.5, y=0.56, showarrow=False,
                 xref="paper", yref="paper",
                 font=dict(size=24, color=ec, family="IBM Plex Mono, monospace")),
            dict(text="ERR RATE", x=0.5, y=0.40, showarrow=False,
                 xref="paper", yref="paper",
                 font=dict(size=8, color=C["text_lo"], family="IBM Plex Mono, monospace")),
        ],
    )
    return fig


# ── 4. Top 10 error messages  (vw_common_errors_top10) ───────────────────────
def chart_top_errors(df, svc=None):
    data = common_errors_top_n(df, top_n=10, service_name=svc, days_back=30).copy()
    if data.empty:
        return _empty()
    data["short"] = data["message"].str[:52].str.strip() + "…"
    data["label"] = (data["service_name"].str.replace("-service", "", regex=False)
                     + "  ›  " + data["short"])
    fig = go.Figure(go.Bar(
        x=data["occurrences"], y=data["label"],
        orientation="h",
        marker=dict(
            color=data["occurrences"],
            colorscale=[[0, C["bg_float"]], [0.4, C["amber_dim"]], [1, C["red"]]],
            line=dict(width=0), showscale=False,
        ),
        text=data["occurrences"].apply(lambda n: f"{n:,}×"),
        textposition="outside",
        textfont=dict(size=9, color=C["text_mid"]),
        hovertemplate="<b>%{y}</b><br>Occurrences: %{x:,}<extra></extra>",
    ))
    fig.update_layout(**_L(
        h=360, title=f"Top Error Messages{_tag('30d')}",
        xa=dict(showgrid=False, showticklabels=False),
        ya=dict(tickfont=dict(size=9, color=C["text_mid"]), autorange="reversed"),
        margin=dict(l=12, r=60, t=40, b=12),
    ))
    return fig


# ── 5. Daily stacked volume  (vw_volume_trends_daily) ────────────────────────
def chart_daily_stacked(df, svc=None):
    data = volume_trends_daily(df, service_name=svc, days_back=30)
    if data.empty:
        return _empty()
    daily = (data.groupby(["day", "service_name"])["log_count"]
             .sum().reset_index().sort_values("day"))
    svcs    = sorted(daily["service_name"].unique())
    palette = [C["blue"], C["amber"], C["green"], C["red"], C["purple"], C["teal"]]
    fig     = go.Figure()
    for i, s in enumerate(svcs):
        d = daily[daily["service_name"] == s]
        col = palette[i % len(palette)]
        r, g, b = _hex_to_rgb(col)
        fig.add_trace(go.Scatter(
            x=d["day"], y=d["log_count"],
            name=s.replace("-service", ""),
            mode="lines", stackgroup="one",
            line=dict(width=0.8, color=col),
            fillcolor=f"rgba({r},{g},{b},0.4)",
            hovertemplate=f"<b>{s}</b>  %{{x|%b %d}}  —  %{{y:,}} logs<extra></extra>",
        ))
    fig.update_layout(**_L(
        h=280, title=f"Daily Volume by Service{_tag('30d · stacked')}",
        xa=dict(tickformat="%b %d", dtick=7 * 86400000),
        lg=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1,
                font=dict(size=9)),
    ))
    return fig


# ── 6. Level stacked bar per service  (vw_level_distribution) ────────────────
def chart_level_stacked(df, svc=None):
    data = level_distribution(df, days_back=30)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    fig = go.Figure()
    for lvl in ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]:
        s = data[data["level"] == lvl]
        if s.empty:
            continue
        fig.add_trace(go.Bar(
            name=lvl, x=s["service_name"], y=s["log_count"],
            marker=dict(color=LVL[lvl], line=dict(width=0)),
            hovertemplate=f"<b>%{{x}}</b>  ·  {lvl}: %{{y:,}}<extra></extra>",
        ))
    fig.update_layout(
        **_L(h=300, title=f"Level Composition by Service{_tag('30d')}",
             xa=dict(tickangle=-20, tickfont=dict(size=9)),
             lg=dict(orientation="h", yanchor="bottom", y=1.02,
                     xanchor="right", x=1)),
        barmode="stack", bargap=0.3,
    )
    return fig


# ── 7. Activity heatmap  (vw_activity_heatmap) ───────────────────────────────
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
        x=[f"{h:02d}" for h in range(24)],
        y=day_names,
        colorscale=[
            [0.0,  C["bg_inset"]],
            [0.25, "#12202E"],
            [0.55, "#1E3A52"],
            [0.80, C["amber_dim"]],
            [1.0,  C["amber"]],
        ],
        hovertemplate="<b>%{y}  %{x}:00</b><br>%{z:,} events<extra></extra>",
        showscale=True,
        colorbar=dict(
            thickness=7, len=0.85,
            tickfont=dict(size=8, color=C["text_lo"]),
            bgcolor="rgba(0,0,0,0)", bordercolor=C["rule"],
            outlinecolor=C["rule"],
        ),
        xgap=2, ygap=2,
    ))
    fig.update_layout(**_L(
        h=265, title=f"Activity Heatmap{_tag('30d · hour × weekday')}",
        xa=dict(side="bottom", tickvals=list(range(0, 24, 3)),
                ticktext=[f"{h:02d}:00" for h in range(0, 24, 3)],
                tickfont=dict(size=9)),
        ya=dict(autorange="reversed", tickfont=dict(size=9)),
    ))
    return fig


# ── 8. Spike detection  (vw_error_spike_detection) ───────────────────────────
def chart_spike_detection(df, svc=None):
    data = error_spike_detection(df)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    sc = {"CRITICAL": C["red"], "ELEVATED": C["orange"], "NORMAL": C["green"]}
    fig = go.Figure()
    fig.add_trace(go.Bar(
        x=data["service_name"], y=[1.0] * len(data),
        marker=dict(color=C["bg_float"], line=dict(width=0)),
        showlegend=False, hoverinfo="skip",
    ))
    fig.add_trace(go.Bar(
        x=data["service_name"], y=data["spike_ratio"].fillna(0),
        marker=dict(color=[sc.get(s, C["text_lo"]) for s in data["spike_status"]],
                    opacity=0.85, line=dict(width=0)),
        text=[f"{v:.1f}×" if pd.notna(v) else "N/A" for v in data["spike_ratio"]],
        textposition="outside",
        textfont=dict(size=10, color=C["text_hi"]),
        customdata=data[["errors_last_1h", "avg_daily_errors_7d"]].values,
        hovertemplate=(
            "<b>%{x}</b><br>"
            "Spike ratio   : <b>%{y:.2f}×</b> baseline<br>"
            "Errors last 1h: %{customdata[0]}<br>"
            "7d daily avg  : %{customdata[1]:.1f}<extra></extra>"
        ),
    ))
    fig.update_layout(
        **_L(h=265, title=f"Error Spike Detection{_tag('1h vs 7d baseline')}",
             ya=dict(title=dict(text="× baseline"))),
        barmode="overlay", bargap=0.35,
    )
    for y_val, lbl, col in [(1.5, "ELEVATED 1.5×", C["orange"]),
                             (3.0, "CRITICAL 3.0×", C["red"])]:
        fig.add_hline(y=y_val, line_dash="dash", line_color=col,
                      line_width=1, opacity=0.55,
                      annotation=dict(text=lbl, font=dict(size=8, color=col),
                                      xanchor="right", x=1))
    return fig


# ── 9. Silent services  (vw_silent_services) ─────────────────────────────────
def chart_silent_services(df):
    data = silent_services(df, silent_minutes=10)
    if data.empty:
        fig = go.Figure()
        fig.add_annotation(
            text="✓  ALL SERVICES ACTIVE", x=0.5, y=0.5,
            showarrow=False, xref="paper", yref="paper",
            font=dict(color=C["green"], size=11, family="IBM Plex Mono, monospace"),
        )
        fig.update_layout(**_L(h=200))
        return fig
    fig = go.Figure(go.Bar(
        x=data["minutes_silent"], y=data["service_name"],
        orientation="h",
        marker=dict(
            color=[C["red"] if m > 30 else C["orange"] if m > 15 else C["amber"]
                   for m in data["minutes_silent"]],
            line=dict(width=0),
        ),
        text=[f"{m:.0f} min" for m in data["minutes_silent"]],
        textposition="outside",
        textfont=dict(size=10, color=C["text_hi"]),
        hovertemplate=(
            "<b>%{y}</b><br>"
            "Silent for: <b>%{x:.1f} min</b><br>"
            "Last seen : %{customdata}<extra></extra>"
        ),
        customdata=data["last_log_at"].dt.strftime("%Y-%m-%d %H:%M"),
    ))
    fig.update_layout(**_L(
        h=max(200, 40 + 36 * len(data)),
        title=f"Silent Services{_tag('>10 min no logs')}",
        xa=dict(title=dict(text="minutes silent"), showgrid=True),
        ya=dict(tickfont=dict(size=10, color=C["text_hi"])),
    ))
    fig.add_vline(x=30, line_dash="dash", line_color=C["red"],
                  line_width=1, opacity=0.5,
                  annotation=dict(text="30 min", font=dict(size=8, color=C["red"])))
    return fig


# ── 10. Top noisy services  (vw_top_noisy_services) ──────────────────────────
def chart_top_noisy(df, svc=None):
    data = top_noisy_services(df)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    palette = [C["blue"], C["amber"], C["green"], C["red"], C["purple"], C["teal"]]
    fig = go.Figure(go.Bar(
        x=data["service_name"],
        y=data["total_logs"],
        marker=dict(
            color=[palette[i % len(palette)] for i in range(len(data))],
            line=dict(width=0),
            opacity=0.85,
        ),
        text=[f"{p:.1f}%" for p in data["pct_of_total"]],
        textposition="outside",
        textfont=dict(size=9, color=C["text_mid"]),
        customdata=data[["pct_of_total"]].values,
        hovertemplate=(
            "<b>%{x}</b><br>"
            "Log volume : <b>%{y:,}</b><br>"
            "% of total : %{customdata[0]:.1f}%<extra></extra>"
        ),
    ))
    fig.update_layout(**_L(
        h=265, title=f"Log Volume by Service{_tag('24h · noisy ranking')}",
        ya=dict(title=dict(text="log count")),
    ))
    return fig


# ── 11. Warn rate  (vw_warn_rate_24h) ────────────────────────────────────────
def chart_warn_rate(df, svc=None):
    data = warn_rate_24h(df).sort_values("warn_rate_percent", ascending=False)
    if svc:
        data = data[data["service_name"] == svc]
    if data.empty:
        return _empty()
    fig = go.Figure(go.Bar(
        x=data["service_name"], y=data["warn_rate_percent"],
        marker=dict(
            color=[C["orange"] if r > 10 else (C["amber"] if r > 5 else C["text_lo"])
                   for r in data["warn_rate_percent"]],
            line=dict(width=0),
        ),
        text=[f"{r:.1f}%" for r in data["warn_rate_percent"]],
        textposition="outside",
        textfont=dict(size=9, color=C["text_mid"]),
        customdata=data[["warn_count", "total_logs"]].values,
        hovertemplate=(
            "<b>%{x}</b><br>"
            "Warn rate : <b>%{y:.2f}%</b><br>"
            "Warnings  : %{customdata[0]:,} / %{customdata[1]:,}<extra></extra>"
        ),
    ))
    fig.update_layout(**_L(
        h=265, title=f"Warning Rate{_tag('24h · leading indicator')}",
        ya=dict(title=dict(text="warn %")),
    ))
    fig.add_hline(y=10, line_dash="dash", line_color=C["orange"],
                  line_width=1, opacity=0.5,
                  annotation=dict(text="10%", font=dict(size=8, color=C["orange"])))
    return fig


# ── 12. MTBE  (vw_mtbe_per_service) ─────────────────────────────────────────
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
        return C["green"] if r > 0.6 else (C["amber"] if r > 0.3 else C["red"])
    fig = go.Figure(go.Bar(
        x=data["avg_minutes_between_errors"],
        y=data["service_name"],
        orientation="h",
        marker=dict(color=[_c(v) for v in data["avg_minutes_between_errors"]],
                    line=dict(width=0)),
        text=[f"{v:.0f} min" for v in data["avg_minutes_between_errors"]],
        textposition="outside",
        textfont=dict(size=10, color=C["text_hi"]),
        customdata=data[["total_errors"]].values,
        hovertemplate=(
            "<b>%{y}</b><br>"
            "Avg between errors : <b>%{x:.1f} min</b><br>"
            "Total errors (7d)  : %{customdata[0]:,}<extra></extra>"
        ),
    ))
    fig.update_layout(**_L(
        h=270, title=f"Mean Time Between Errors{_tag('7d · higher = more stable')}",
        xa=dict(title=dict(text="minutes between errors")),
        ya=dict(tickfont=dict(size=10, color=C["text_hi"])),
    ))
    return fig


# ═══════════════════════════════════════════════════════════════════════════════
# UI COMPONENTS
# ═══════════════════════════════════════════════════════════════════════════════

def _kpi_tile(label, value, sub, accent, delta=None, delta_color=None):
    return html.Div([
        html.Div([
            html.Span(label, style={
                "fontSize": "8px", "letterSpacing": "2px", "color": C["text_lo"],
                "textTransform": "uppercase", "fontFamily": "'IBM Plex Mono', monospace",
            }),
            html.Span(f" {delta}", style={
                "fontSize": "9px", "color": delta_color or C["text_lo"],
                "marginLeft": "8px", "fontWeight": "700",
            }) if delta else None,
        ], style={"marginBottom": "10px", "display": "flex", "alignItems": "center"}),
        html.Div(str(value), style={
            "fontSize": "34px", "fontWeight": "700", "color": accent,
            "lineHeight": "1", "letterSpacing": "-1px",
            "fontFamily": "'IBM Plex Mono', monospace",
        }),
        html.Div(sub, style={
            "fontSize": "8px", "color": C["text_lo"], "marginTop": "8px",
            "letterSpacing": "0.5px", "fontFamily": "'IBM Plex Mono', monospace",
        }),
        # Accent bar
        html.Div(style={
            "position": "absolute", "bottom": "0", "left": "0", "right": "0",
            "height": "1px", "background": accent, "opacity": "0.5",
        }),
    ], style={
        "background": C["bg_raised"],
        "border": f"1px solid {C['rule_hi']}",
        "borderRadius": "3px", "padding": "18px 20px 16px",
        "flex": "1", "minWidth": "130px", "position": "relative",
    })


def _status_badge(status):
    cols = {"HEALTHY": C["green"], "DEGRADED": C["orange"],
            "CRITICAL": C["red"],  "SILENT": C["text_lo"]}
    col  = cols.get(status, C["text_lo"])
    dot  = "●" if status in ("CRITICAL", "DEGRADED") else "○"
    return html.Span([
        html.Span(dot + " ", style={"color": col, "fontSize": "7px"}),
        html.Span(status),
    ], style={
        "color": col, "fontSize": "9px", "fontWeight": "600",
        "letterSpacing": "1px", "fontFamily": "'IBM Plex Mono', monospace",
    })


def _health_table(df):
    data = service_health_summary(df)
    if data.empty:
        return html.Div("NO DATA", style={"color": C["text_lo"], "padding": "16px",
                                          "fontSize": "10px"})
    th = lambda t, left=False: html.Th(t, style={
        "padding": "8px 14px", "fontSize": "8px", "color": C["text_lo"],
        "textTransform": "uppercase", "letterSpacing": "1.5px",
        "borderBottom": f"1px solid {C['rule_hi']}",
        "textAlign": "left" if left else "right",
        "fontFamily": "'IBM Plex Mono', monospace",
        "whiteSpace": "nowrap",
    })
    header = html.Tr([
        th("Service", left=True), th("Status", left=True),
        th("Logs 24h"), th("Errors"), th("Warns"),
        th("Err %"), th("Warn %"), th("Err/1h"), th("Last seen"),
    ])
    rows = []
    for idx, r in data.iterrows():
        bar_w = min(r["error_rate_24h"] * 4, 100)
        bar_c = C["red"] if r["error_rate_24h"] > 10 else (
                C["orange"] if r["error_rate_24h"] > 5 else C["green"])
        td = lambda val, right=True, hi=False, col=None: html.Td(val, style={
            "padding": "7px 14px", "fontSize": "11px",
            "fontFamily": "'IBM Plex Mono', monospace",
            "color": col or (C["text_hi"] if hi else C["text_mid"]),
            "textAlign": "right" if right else "left",
            "whiteSpace": "nowrap",
        })
        bg = C["bg_stripe"] if idx % 2 == 0 else "transparent"
        rows.append(html.Tr([
            td(r["service_name"].replace("-service", ""), right=False, hi=True),
            html.Td(_status_badge(r["status"]), style={
                "padding": "7px 14px", "background": bg}),
            td(f"{r['total_logs_24h']:,}"),
            td(f"{r['errors_last_1h']:,}", col=C["red"] if r["errors_last_1h"] > 0 else C["text_lo"]),
            td(f"{int(r['total_logs_24h'] * r['warn_rate_24h'] / 100):,}",
               col=C["orange"] if r["warn_rate_24h"] > 5 else C["text_lo"]),
            html.Td(html.Div([
                html.Div(style={
                    "width": f"{bar_w}%", "minWidth": "2px", "height": "3px",
                    "background": bar_c, "borderRadius": "1px", "marginBottom": "3px",
                }),
                html.Span(f"{r['error_rate_24h']:.1f}%", style={
                    "fontSize": "10px", "color": bar_c,
                    "fontFamily": "'IBM Plex Mono', monospace",
                }),
            ], style={"padding": "7px 14px", "minWidth": "70px"})),
            td(f"{r['warn_rate_24h']:.1f}%",
               col=C["orange"] if r["warn_rate_24h"] > 10 else C["text_mid"]),
            td(f"{r['errors_last_1h']}",
               col=C["red"] if r["errors_last_1h"] > 10 else C["text_mid"]),
            td(r["last_log_at"].strftime("%H:%M:%S") if pd.notna(r["last_log_at"]) else "—"),
        ], style={"borderBottom": f"1px solid {C['rule']}", "background": bg}))
    return html.Div(
        html.Table([html.Thead(header), html.Tbody(rows)],
                   style={"width": "100%", "borderCollapse": "collapse"}),
        style={"overflowX": "auto"},
    )


def _error_feed(df):
    data = recent_critical_events(df, limit=25)
    if data.empty:
        return html.Div("NO ERRORS IN DATASET  ✓",
                        style={"color": C["green"], "padding": "20px",
                               "fontSize": "10px", "letterSpacing": "1.5px",
                               "fontFamily": "'IBM Plex Mono', monospace"})
    items = []
    for i, (_, row) in enumerate(data.iterrows()):
        svc_short = row["service_name"].replace("-service", "").upper()
        ts_str    = row["timestamp"].strftime("%m-%d  %H:%M:%S")
        items.append(html.Div([
            html.Div([
                html.Span(ts_str, style={
                    "fontSize": "9px", "color": C["text_lo"],
                    "fontFamily": "'IBM Plex Mono', monospace",
                    "marginRight": "12px", "letterSpacing": "0.5px",
                }),
                html.Span(svc_short, style={
                    "fontSize": "8px", "color": C["amber"],
                    "fontFamily": "'IBM Plex Mono', monospace",
                    "letterSpacing": "1.5px", "fontWeight": "700",
                    "background": C["amber_subtle"],
                    "padding": "1px 6px", "borderRadius": "2px",
                }),
            ], style={"marginBottom": "4px", "display": "flex", "alignItems": "center"}),
            html.Div(row["message"], style={
                "fontSize": "11px", "color": C["text_mid"],
                "fontFamily": "'IBM Plex Mono', monospace",
                "lineHeight": "1.5",
            }),
        ], style={
            "padding": "10px 16px",
            "borderBottom": f"1px solid {C['rule']}",
            "borderLeft": f"2px solid {C['red'] if i < 5 else C['rule_hi']}",
        }))
    return html.Div(items, style={"overflowY": "auto", "maxHeight": "360px"})


def _panel(children, col_span=1, label=None):
    return html.Div([
        html.Div(label, style={
            "fontSize": "8px", "letterSpacing": "2px", "color": C["text_lo"],
            "fontFamily": "'IBM Plex Mono', monospace",
            "textTransform": "uppercase", "marginBottom": "12px",
            "paddingBottom": "8px", "borderBottom": f"1px solid {C['rule']}",
        }) if label else None,
        children,
    ], style={
        "background": C["bg_raised"],
        "border": f"1px solid {C['rule_hi']}",
        "borderRadius": "3px", "padding": "18px",
        "gridColumn": f"span {col_span}",
        "overflow": "hidden",
    })


def _section(label, children):
    return html.Div([
        html.Div(label, style={
            "fontSize": "8px", "letterSpacing": "3px", "color": C["text_lo"],
            "fontFamily": "'IBM Plex Mono', monospace",
            "textTransform": "uppercase", "marginBottom": "12px",
            "paddingBottom": "8px", "borderBottom": f"1px solid {C['rule']}",
            "display": "flex", "alignItems": "center",
        }),
        children,
    ], style={"marginBottom": "24px"})


# ═══════════════════════════════════════════════════════════════════════════════
# APP ASSEMBLY
# ═══════════════════════════════════════════════════════════════════════════════

GOOGLE_FONTS = (
    "https://fonts.googleapis.com/css2?"
    "family=IBM+Plex+Mono:wght@300;400;500;600;700&"
    "display=swap"
)

def build_app(df: pd.DataFrame) -> Dash:
    app  = Dash(__name__, title="LogStream · Observability")
    kpis = _kpis(df)
    cfg  = {"displayModeBar": False}

    # All service options for the filter dropdown
    svc_options = [{"label": "All Services", "value": "__all__"}] + [
        {"label": s, "value": s} for s in sorted(df["service_name"].unique())
    ]

    # Inject global CSS
    app.index_string = f"""<!DOCTYPE html>
<html>
<head>
    {{%metas%}}
    <title>{{%title%}}</title>
    {{%favicon%}}
    {{%css%}}
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="stylesheet" href="{GOOGLE_FONTS}">
    <style>
        *, *::before, *::after {{ box-sizing: border-box; }}
        html, body {{ margin: 0; padding: 0; background: {C['bg_base']}; }}
        ::-webkit-scrollbar {{ width: 4px; height: 4px; }}
        ::-webkit-scrollbar-track {{ background: {C['bg_base']}; }}
        ::-webkit-scrollbar-thumb {{ background: {C['rule_bright']}; border-radius: 2px; }}
        ::-webkit-scrollbar-thumb:hover {{ background: {C['text_lo']}; }}
        .Select-control, .Select-menu-outer {{
            background: {C['bg_float']} !important;
            border-color: {C['rule_bright']} !important;
            color: {C['text_hi']} !important;
        }}
        .Select-option {{
            background: {C['bg_float']} !important;
            color: {C['text_mid']} !important;
        }}
        .Select-option.is-focused {{
            background: {C['rule_hi']} !important;
        }}
        .Select-value-label {{ color: {C['amber']} !important; }}
    </style>
</head>
<body>
    {{%app_entry%}}
    <footer>{{%config%}}{{%scripts%}}{{%renderer%}}</footer>
</body>
</html>"""

    app.layout = html.Div([

        # ══ HEADER ═══════════════════════════════════════════════════════════
        html.Div([
            # Wordmark
            html.Div([
                html.Span("▣ ", style={"color": C["amber"], "fontSize": "13px",
                                        "marginRight": "10px"}),
                html.Span("LOGSTREAM", style={
                    "fontFamily": "'IBM Plex Mono', monospace",
                    "fontWeight": "700", "fontSize": "13px",
                    "letterSpacing": "5px", "color": C["text_hi"],
                }),
                html.Span(" / OBSERVABILITY", style={
                    "fontFamily": "'IBM Plex Mono', monospace",
                    "fontSize": "10px", "letterSpacing": "2px",
                    "color": C["text_lo"], "marginLeft": "6px",
                }),
            ], style={"display": "flex", "alignItems": "center"}),

            # Centre: service filter
            html.Div([
                html.Span("SERVICE  ", style={
                    "fontSize": "8px", "color": C["text_lo"],
                    "letterSpacing": "1.5px", "marginRight": "8px",
                    "fontFamily": "'IBM Plex Mono', monospace",
                }),
                dcc.Dropdown(
                    id="svc-filter",
                    options=svc_options,
                    value="__all__",
                    clearable=False,
                    searchable=False,
                    style={
                        "width": "240px", "fontSize": "11px",
                        "fontFamily": "'IBM Plex Mono', monospace",
                        "background": C["bg_float"],
                        "border": f"1px solid {C['rule_bright']}",
                        "borderRadius": "3px", "color": C["amber"],
                    },
                ),
            ], style={"display": "flex", "alignItems": "center"}),

            # Right: data range + clock
            html.Div([
                html.Div([
                    html.Span("RANGE  ", style={
                        "fontSize": "8px", "color": C["text_lo"],
                        "letterSpacing": "1px",
                        "fontFamily": "'IBM Plex Mono', monospace",
                    }),
                    html.Span(
                        f"{df['timestamp'].min().strftime('%Y-%m-%d')}  →  "
                        f"{df['timestamp'].max().strftime('%Y-%m-%d')}",
                        style={"fontSize": "9px", "color": C["amber"],
                               "fontFamily": "'IBM Plex Mono', monospace"},
                    ),
                ], style={"marginBottom": "3px", "textAlign": "right"}),
                html.Div(id="live-clock", style={
                    "fontSize": "11px", "color": C["text_mid"],
                    "fontFamily": "'IBM Plex Mono', monospace",
                    "textAlign": "right",
                }),
                dcc.Interval(id="clock-tick", interval=1000, n_intervals=0),
            ]),
        ], style={
            "display": "flex", "justifyContent": "space-between",
            "alignItems": "center", "padding": "13px 28px",
            "borderBottom": f"1px solid {C['rule_hi']}",
            "background": C["bg_raised"],
            "position": "sticky", "top": "0", "zIndex": "200",
        }),

        # ══ MAIN CONTENT ═════════════════════════════════════════════════════
        html.Div([

            # ── KPI ROW ──────────────────────────────────────────────────
            _section("SNAPSHOT  ·  LAST 24 HOURS",
                html.Div([
                    _kpi_tile("Total Events",  f"{kpis['total']:,}",
                              f"~{kpis['throughput']}/hr avg", C["text_hi"]),
                    _kpi_tile("Errors",         f"{kpis['errors']:,}",
                              "level = ERROR",  C["red"],
                              delta=kpis["trend"], delta_color=kpis["trend_color"]),
                    _kpi_tile("Warnings",        f"{kpis['warns']:,}",
                              "level = WARN  ·  leading indicator", C["orange"]),
                    _kpi_tile("Error Rate",      f"{kpis['erate']}%",
                              "errors / total  ·  SLO < 5%",
                              C["red"] if kpis["erate"] > 10
                              else C["orange"] if kpis["erate"] > 5 else C["green"]),
                    _kpi_tile("Services",        f"{kpis['services']}",
                              f"monitored  ·  {len(df):,} total records", C["blue"]),
                ], style={"display": "flex", "gap": "10px", "flexWrap": "wrap"}),
            ),

            # ── TRAFFIC & COMPOSITION ─────────────────────────────────
            _section("TRAFFIC  ·  VOLUME & COMPOSITION", html.Div([
                html.Div([
                    _panel(dcc.Graph(id="g-timeline", config=cfg), col_span=2),
                    _panel(dcc.Graph(id="g-donut",    config=cfg), col_span=1),
                ], style={"display": "grid",
                          "gridTemplateColumns": "2fr 1fr", "gap": "10px"}),
                html.Div(style={"height": "10px"}),
                _panel(dcc.Graph(id="g-daily", config=cfg)),
            ])),

            # ── RELIABILITY ───────────────────────────────────────────
            _section("RELIABILITY  ·  ERROR ANALYSIS", html.Div([
                html.Div([
                    _panel(dcc.Graph(id="g-errrate",  config=cfg)),
                    _panel(dcc.Graph(id="g-toperrs",  config=cfg)),
                ], style={"display": "grid",
                          "gridTemplateColumns": "1fr 1fr", "gap": "10px"}),
            ])),

            # ── SERVICE HEALTH TABLE ──────────────────────────────────
            _section("SERVICE HEALTH  ·  24H WINDOW", html.Div(
                id="health-table",
                style={"background": C["bg_raised"],
                       "border": f"1px solid {C['rule_hi']}",
                       "borderRadius": "3px"},
            )),

            # ── PATTERNS ─────────────────────────────────────────────
            _section("PATTERNS  ·  BEHAVIORAL ANALYSIS", html.Div([
                html.Div([
                    _panel(dcc.Graph(id="g-heatmap",  config=cfg)),
                    _panel(dcc.Graph(id="g-levelbar", config=cfg)),
                ], style={"display": "grid",
                          "gridTemplateColumns": "1fr 1fr", "gap": "10px"}),
            ])),

            # ── SIGNALS & ANOMALY ─────────────────────────────────────
            _section("SIGNALS  ·  ANOMALY & RELIABILITY", html.Div([
                html.Div([
                    _panel(dcc.Graph(id="g-noisy",  config=cfg)),
                    _panel(dcc.Graph(id="g-spike",  config=cfg)),
                ], style={"display": "grid",
                          "gridTemplateColumns": "1fr 1fr", "gap": "10px"}),
                html.Div(style={"height": "10px"}),
                html.Div([
                    _panel(dcc.Graph(id="g-warn",   config=cfg)),
                    _panel(dcc.Graph(id="g-mtbe",   config=cfg)),
                ], style={"display": "grid",
                          "gridTemplateColumns": "1fr 1fr", "gap": "10px"}),
                html.Div(style={"height": "10px"}),
                _panel(dcc.Graph(id="g-silent", config=cfg)),
            ])),

            # ── LIVE ERROR FEED ───────────────────────────────────────
            _section(
                html.Span([
                    html.Span("●  ", style={"color": C["red"], "fontSize": "8px"}),
                    "LIVE ERROR FEED  ·  MOST RECENT 25 ERRORS",
                ]),
                html.Div(
                    id="error-feed",
                    style={"background": C["bg_raised"],
                           "border": f"1px solid {C['rule_hi']}",
                           "borderLeft": f"2px solid {C['red']}",
                           "borderRadius": "0 3px 3px 0"},
                ),
            ),

            # ── FOOTER ────────────────────────────────────────────────
            html.Div([
                html.Span("▣  LOGSTREAM OBSERVABILITY PLATFORM", style={
                    "color": C["amber"], "fontWeight": "600",
                    "letterSpacing": "2px", "fontSize": "8px",
                }),
                html.Span(
                    f"  ·  {len(df):,} records  ·  "
                    f"{df['service_name'].nunique()} services  ·  Team 13",
                    style={"color": C["text_lo"], "fontSize": "8px"},
                ),
            ], style={
                "textAlign": "center", "padding": "20px",
                "borderTop": f"1px solid {C['rule_hi']}",
                "fontFamily": "'IBM Plex Mono', monospace",
            }),

        ], style={"padding": "24px 28px", "maxWidth": "1560px", "margin": "0 auto"}),

    ], style={
        "background": C["bg_base"], "minHeight": "100vh",
        "fontFamily": "'IBM Plex Mono', monospace", "color": C["text_mid"],
    })

    # ── CALLBACKS ─────────────────────────────────────────────────────────────

    @app.callback(Output("live-clock", "children"),
                  Input("clock-tick", "n_intervals"))
    def _clock(n):
        return datetime.now(timezone.utc).strftime("UTC  %Y-%m-%d  %H:%M:%S")

    def _svc(val):
        return None if val == "__all__" else val

    @app.callback(
        Output("g-timeline", "figure"),
        Output("g-donut",    "figure"),
        Output("g-daily",    "figure"),
        Output("g-errrate",  "figure"),
        Output("g-toperrs",  "figure"),
        Output("g-heatmap",  "figure"),
        Output("g-levelbar", "figure"),
        Output("g-noisy",    "figure"),
        Output("g-spike",    "figure"),
        Output("g-warn",     "figure"),
        Output("g-mtbe",     "figure"),
        Output("g-silent",   "figure"),
        Output("health-table", "children"),
        Output("error-feed",   "children"),
        Input("svc-filter",  "value"),
    )
    def update_all(svc_val):
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
            chart_silent_services(df),   # always all services
            _health_table(df),           # always full table
            _error_feed(df),             # always full feed
        )

    return app


# ═══════════════════════════════════════════════════════════════════════════════
# ENTRY POINT
# ═══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LogStream Observability Dashboard")
    parser.add_argument("--data", default=None,
                        help=".json or .csv log file path")
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
    print(f"\n[logstream] → http://{args.host}:{args.port}\n")

    app = build_app(df)
    app.run(debug=False, host=args.host, port=args.port)