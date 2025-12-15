#!/usr/bin/env python3
"""
Initialize input items with default values via OpenHAB REST API.
Usage: python3 input-items-init.py [openhab-url]
"""

import requests
import sys

OPENHAB_URL = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8888'
REST_API = f'{OPENHAB_URL}/rest/items'

items_to_init = [
    ('manualMode', 'OFF'),
    ('temporaryManualMode', 'OFF'),
    ('temporaryManualModeDurationSec', '28800'),
    ('boostMode', 'OFF'),
    ('temporaryBoostMode', 'OFF'),
    ('temporaryBoostModeDurationSec', '600'),
    ('humidityThreshold', '60'),
    ('co2ThresholdLow', '500'),
    ('co2ThresholdMid', '700'),
    ('co2ThresholdHigh', '900'),
    ('manualPower', '50'),
    ('boostPower', '95'),
    ('smokePower', '0'),
    ('gasPower', '95'),
    ('humidityPower', '95'),
    ('co2PowerLow', '15'),
    ('co2PowerMid', '50'),
    ('co2PowerHigh', '95'),
    ('basePower', '50'),
]

print(f'Initializing {len(items_to_init)} items...')
for item_name, default_value in items_to_init:
    try:
        response = requests.post(
            f'{REST_API}/{item_name}/state',
            data=default_value,
            headers={'Content-Type': 'text/plain', 'Accept': 'application/json'}
        )
        if response.status_code in [200, 201, 202]:
            print(f'  ✓ {item_name} = {default_value}')
        else:
            print(f'  ✗ {item_name}: {response.status_code} - {response.text}')
    except Exception as e:
        print(f'  ✗ {item_name}: {e}')

print('Done!')