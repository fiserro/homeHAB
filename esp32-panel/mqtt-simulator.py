#!/usr/bin/env python3
"""
MQTT Simulator for HRV Panel
Simulates the ESP32-P4 panel communication with OpenHAB via MQTT.

Panel behavior:
  - Subscribes to OpenHAB item states (temperature, humidity, co2, hrvOutputPower)
  - Publishes commands when user interacts (mode, power)

Usage:
    pip3 install paho-mqtt
    python3 mqtt-simulator.py

Commands (type in terminal):
    auto    - Send auto mode command
    boost   - Send boost mode command
    manual  - Send manual mode command
    p <N>   - Send power level command (0-100)
    s       - Show current state (received from OpenHAB)
    q       - Quit
"""

import paho.mqtt.client as mqtt
import time
import sys

# Configuration
MQTT_BROKER = "openhab.home"
MQTT_PORT = 1883
PANEL_TOPIC_PREFIX = "homehab-dev/panel"

# Topics panel subscribes to (from OpenHAB)
SUBSCRIBE_TOPICS = [
    "homehab-dev/temperature/state",
    "homehab-dev/airHumidity/state",
    "homehab-dev/co2/state",
    "homehab-dev/hrvOutputPower/state",
    "homehab-dev/manualMode/state",
    "homehab-dev/boostMode/state",
]

# State received from OpenHAB
state = {
    "temperature": "?",
    "airHumidity": "?",
    "co2": "?",
    "hrvOutputPower": "?",
    "manualMode": "?",
    "boostMode": "?",
}


def on_connect(client, userdata, flags, rc):
    print(f"Connected to MQTT broker (rc={rc})")

    # Subscribe to OpenHAB states
    for topic in SUBSCRIBE_TOPICS:
        client.subscribe(topic)
        print(f"  Subscribed: {topic}")

    # Publish online status
    client.publish(f"{PANEL_TOPIC_PREFIX}/status", "online", retain=True)
    print(f"  Published: {PANEL_TOPIC_PREFIX}/status = online")


def on_message(client, userdata, msg):
    topic = msg.topic
    payload = msg.payload.decode()

    # Extract item name from topic (e.g., "homehab-dev/temperature/state" -> "temperature")
    parts = topic.split("/")
    if len(parts) >= 2:
        item_name = parts[1]  # homehab-dev/<item>/state
        if item_name in state:
            state[item_name] = payload
            print(f"<< {item_name}: {payload}")


def print_state():
    print("\n--- Current State (from OpenHAB) ---")
    print(f"  Temperature:    {state['temperature']} C")
    print(f"  Humidity:       {state['airHumidity']} %")
    print(f"  CO2:            {state['co2']} ppm")
    print(f"  HRV Power:      {state['hrvOutputPower']} %")
    print(f"  Manual Mode:    {state['manualMode']}")
    print(f"  Boost Mode:     {state['boostMode']}")
    print("------------------------------------\n")


def main():
    # Create MQTT client
    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message

    # Set last will
    client.will_set(f"{PANEL_TOPIC_PREFIX}/status", "offline", retain=True)

    try:
        print(f"Connecting to {MQTT_BROKER}:{MQTT_PORT}...")
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
    except Exception as e:
        print(f"Failed to connect: {e}")
        sys.exit(1)

    # Start MQTT loop in background
    client.loop_start()

    # Wait for connection
    time.sleep(1)

    # Command loop
    print("\n" + "=" * 50)
    print("HRV Panel MQTT Simulator")
    print("=" * 50)
    print("Simulates panel behavior:")
    print("  - Receives state from OpenHAB")
    print("  - Sends commands to OpenHAB")
    print("")
    print("Commands:")
    print("  auto, boost, manual  - Set HRV mode")
    print("  p <0-100>            - Set power level")
    print("  s                    - Show current state")
    print("  q                    - Quit")
    print("-" * 50)

    try:
        while True:
            cmd = input("> ").strip().lower()

            if cmd == "q":
                break
            elif cmd == "s":
                print_state()
            elif cmd == "auto":
                client.publish(f"{PANEL_TOPIC_PREFIX}/mode/command", "auto")
                print(">> Sent: mode = auto")
            elif cmd == "boost":
                client.publish(f"{PANEL_TOPIC_PREFIX}/mode/command", "boost")
                print(">> Sent: mode = boost")
            elif cmd == "manual":
                client.publish(f"{PANEL_TOPIC_PREFIX}/mode/command", "manual")
                print(">> Sent: mode = manual")
            elif cmd.startswith("p "):
                try:
                    power = int(cmd.split()[1])
                    power = max(0, min(100, power))
                    client.publish(f"{PANEL_TOPIC_PREFIX}/power/command", str(power))
                    print(f">> Sent: power = {power}")
                except (ValueError, IndexError):
                    print("Usage: p <0-100>")
            elif cmd:
                print("Unknown command. Try: auto, boost, manual, p <N>, s, q")

    except KeyboardInterrupt:
        print("\nInterrupted")

    # Cleanup
    client.publish(f"{PANEL_TOPIC_PREFIX}/status", "offline", retain=True)
    client.loop_stop()
    client.disconnect()
    print("Disconnected")


if __name__ == "__main__":
    main()
