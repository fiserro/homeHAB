"""Simple HTTP server to serve the rendered chart PNG."""

import logging
import os
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

log = logging.getLogger("weather-service")


class ChartHandler(BaseHTTPRequestHandler):
    """Serves the weather chart PNG."""

    chart_path = None

    def do_GET(self):
        if self.path == '/weather/chart.rgb565':
            raw_path = self.chart_path.replace('.png', '.rgb565') if self.chart_path else None
            if raw_path and os.path.exists(raw_path):
                with open(raw_path, 'rb') as f:
                    data = f.read()
                self.send_response(200)
                self.send_header('Content-Type', 'application/octet-stream')
                self.send_header('Content-Length', str(len(data)))
                self.send_header('Cache-Control', 'no-cache')
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(503, "Chart not yet rendered")
        elif self.path in ('/weather/chart.png', '/render/d-solo/weather/weather-forecast'):
            if self.chart_path and os.path.exists(self.chart_path):
                with open(self.chart_path, 'rb') as f:
                    data = f.read()
                self.send_response(200)
                self.send_header('Content-Type', 'image/png')
                self.send_header('Content-Length', str(len(data)))
                self.send_header('Cache-Control', 'no-cache')
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(503, "Chart not yet rendered")
        else:
            self.send_error(404, "Not found")

    def log_message(self, format, *args):
        log.debug("HTTP: %s", format % args)


def start_http_server(port: int, chart_path: str):
    """Start HTTP server in a background thread."""
    ChartHandler.chart_path = chart_path
    server = HTTPServer(('0.0.0.0', port), ChartHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    log.info("HTTP server started on port %d, serving %s", port, chart_path)
    return server
