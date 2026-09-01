# Solar Dryer Android

Native Android controller for the ESP32 Smart Solar Dryer REST API.

The app can:

- connect to the dryer by local IP or `solar-dryer.local`
- read inside/outside temperature and humidity
- read live weight, initial weight, remaining percentage and drying notice
- tare and calibrate the HX711 load cell
- set the current load as the initial weight and configure a completion threshold
- force the fan ON/OFF, return it to AUTO mode, configure temperature/humidity logic, and configure schedules
- mirror the text currently shown on the OLED
- display battery voltage/percentage when ESP32 battery-monitor hardware is enabled
- poll the notice endpoint while the app is open

## ESP32

The app expects the REST API endpoints from the Smart Solar Dryer firmware, including `/api/status`, `/api/temp`, `/api/weight`, `/api/fan`, `/api/oled`, `/api/notice`, and `/api/battery`.

## Build

GitHub Actions builds a debug APK on every push to `main` and on manual workflow dispatch.

The project is intentionally dependency-light: it uses the Android SDK, Java, `HttpURLConnection`, and `org.json` only.
