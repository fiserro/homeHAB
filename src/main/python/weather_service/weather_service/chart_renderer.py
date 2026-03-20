"""Renders weather forecast chart as PNG using matplotlib."""

import io
import logging
import os

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from datetime import datetime

from .fetcher import HourlyForecast

log = logging.getLogger("weather-service")

# Panel dark theme colors
BG_COLOR = '#0d1117'
CARD_COLOR = '#161b22'
GRID_COLOR = '#30363d'
TEXT_COLOR = '#ffffff'
TEXT_DIM = '#8b949e'
TEMP_COLOR = '#d29922'
PRECIP_COLOR = '#58a6ff'
WIND_COLOR = '#8b949e'


def render_chart(forecasts: list[HourlyForecast], output_path: str,
                 width: int = 688, height: int = 640):
    """Render weather forecast chart as PNG.

    Includes temperature line, precipitation bars, and wind speed overlay.
    """
    if not forecasts:
        log.warning("No forecast data to render")
        return

    dpi = 100
    fig, ax1 = plt.subplots(figsize=(width / dpi, height / dpi), dpi=dpi)
    fig.patch.set_facecolor(BG_COLOR)
    ax1.set_facecolor(BG_COLOR)

    times = [f.dt for f in forecasts]
    temps = [f.temperature for f in forecasts]
    precips = [f.precip_mm for f in forecasts]
    winds = [f.wind_speed for f in forecasts]

    # Temperature line (left Y axis)
    ax1.plot(times, temps, color=TEMP_COLOR, linewidth=3, marker='o',
             markersize=5, zorder=3, label='Teplota')
    ax1.fill_between(times, temps, alpha=0.15, color=TEMP_COLOR, zorder=2)
    ax1.set_ylabel('°C', color=TEXT_COLOR, fontsize=12)
    ax1.tick_params(axis='y', colors=TEXT_COLOR, labelsize=10)

    # Auto-scale with padding
    temp_min, temp_max = min(temps), max(temps)
    temp_pad = max((temp_max - temp_min) * 0.15, 2)
    ax1.set_ylim(temp_min - temp_pad, temp_max + temp_pad)

    # Precipitation bars (right Y axis)
    ax2 = ax1.twinx()
    bar_width = 0.025  # fraction of day
    ax2.bar(times, precips, width=bar_width, color=PRECIP_COLOR,
            alpha=0.7, zorder=1, label='Srazky')
    ax2.set_ylabel('mm', color=PRECIP_COLOR, fontsize=10)
    ax2.tick_params(axis='y', colors=PRECIP_COLOR, labelsize=9)

    # Precip axis: at least 0-5mm range
    precip_max = max(max(precips) if precips else 0, 5)
    ax2.set_ylim(0, precip_max * 1.3)

    # Wind speed overlay (dashed line, no axis)
    ax1.plot(times, winds, color=WIND_COLOR, linewidth=1, linestyle='--',
             alpha=0.5, zorder=2, label='Vitr')

    # X axis formatting
    ax1.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M'))
    ax1.xaxis.set_major_locator(mdates.HourLocator(interval=3))
    ax1.tick_params(axis='x', colors=TEXT_COLOR, labelsize=10)

    # Grid
    ax1.grid(True, color=GRID_COLOR, linewidth=0.5, alpha=0.5)
    ax1.set_axisbelow(True)

    # Spines
    for spine in ax1.spines.values():
        spine.set_color(GRID_COLOR)
    for spine in ax2.spines.values():
        spine.set_color(GRID_COLOR)

    # Legend
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc='upper right',
               facecolor=CARD_COLOR, edgecolor=GRID_COLOR,
               labelcolor=TEXT_COLOR, fontsize=9)

    plt.tight_layout(pad=1.0)

    # Save to temp file then rename (atomic)
    tmp_path = output_path + '.tmp.png'
    fig.savefig(tmp_path, format='png', facecolor=fig.get_facecolor(),
                edgecolor='none')
    plt.close(fig)
    os.replace(tmp_path, output_path)

    log.info("Chart rendered: %s (%d bytes)", output_path,
             os.path.getsize(output_path))

    # Also render as raw RGB565 for ESP32 direct display (no PNG decoding)
    raw_path = output_path.replace('.png', '.rgb565')
    _convert_to_rgb565(output_path, raw_path)


def _convert_to_rgb565(png_path: str, raw_path: str):
    """Convert PNG to raw RGB565 binary with 4-byte header (width, height)."""
    try:
        from PIL import Image
        import struct
        import numpy as np

        img = Image.open(png_path).convert('RGB')
        arr = np.array(img, dtype=np.uint16)
        r = (arr[:, :, 0] >> 3).astype(np.uint16)
        g = (arr[:, :, 1] >> 2).astype(np.uint16)
        b = (arr[:, :, 2] >> 3).astype(np.uint16)
        rgb565 = (r << 11) | (g << 5) | b
        pixels = rgb565.astype('<u2').tobytes()

        tmp = raw_path + '.tmp.raw'
        with open(tmp, 'wb') as f:
            f.write(struct.pack('<HH', img.width, img.height))
            f.write(pixels)
        os.replace(tmp, raw_path)

        log.info("RGB565 rendered: %s (%d bytes, %dx%d)",
                 raw_path, os.path.getsize(raw_path), img.width, img.height)
    except Exception as e:
        log.error("RGB565 conversion failed: %s", e)
