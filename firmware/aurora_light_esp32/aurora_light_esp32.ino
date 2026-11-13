/*
 * AURORA RGB light - ESP32 reference firmware
 * Protocol: 11 | CMD | LEN | DATA... | CHECKSUM
 * CHECKSUM = sum of all previous bytes & 0xFF
 *
 * GATT: 0xBBB0 service, 0xBBB1 write, 0xBBB2 optional notify.
 * The Android app only writes to 0xBBB1; it does not read or subscribe.
 *
 * Libraries: FastLED 3.6.x, NimBLE-Arduino 1.4.3 (not 2.x)
 */

#include <Arduino.h>
#include <esp_mac.h>
#include <NimBLEDevice.h>
#include <FastLED.h>

#define LED_PIN        5
#define NUM_LEDS       60
#define LED_TYPE       WS2812B
#define COLOR_ORDER    GRB
#define MAX_MILLIAMPS  2500

static const char* SERVICE_UUID = "BBB0";
static const char* CONTROL_UUID = "BBB1";
static const char* STATUS_UUID  = "BBB2";
static const char* INFO_UUID    = "BBB3";

struct LightState {
  bool power = true;
  uint8_t brightness = 72;
  uint8_t mode = 2;       // 0 static, 2 running, 3 breathe, 4 flame, 5 rhythm
  uint8_t colorIndex = 5; // 1-9
  uint8_t speed = 60;
  uint8_t ledCount = NUM_LEDS;
  bool colorOn = true;
} state;

CRGB leds[NUM_LEDS];
NimBLECharacteristic* controlChar = nullptr;
NimBLECharacteristic* statusChar = nullptr;
char deviceName[24];

uint32_t hueOffset = 0;
uint32_t lastRender = 0;
uint32_t selfTestUntil = 0;

uint8_t checksum(const uint8_t* data, size_t len) {
  uint8_t sum = 0;
  for (size_t i = 0; i < len; i++) sum = (uint8_t)(sum + data[i]);
  return sum;
}

CRGB colorForIndex(uint8_t index) {
  switch (index) {
    case 1: return CRGB(255, 0, 0);
    case 2: return CRGB(255, 127, 0);
    case 3: return CRGB(255, 255, 0);
    case 4: return CRGB(0, 255, 0);
    case 5: return CRGB(0, 255, 255);
    case 6: return CRGB(0, 0, 255);
    case 7: return CRGB(128, 0, 255);
    case 8: return CRGB(255, 255, 255);
    case 9: return CRGB(255, 0, 255);
    default: return CRGB(0, 255, 255);
  }
}

uint8_t appModeToCommand(uint8_t appMode) {
  if (appMode == 0) return 0;
  switch (appMode) {
    case 1: return 2;
    case 2: return 3;
    case 3: return 5;
    case 4: return 4;
    default: return 2;
  }
}

void applyCommand(uint8_t cmd, const uint8_t* data, uint8_t len) {
  switch (cmd) {
    case 0x01: // power
      if (len == 1) state.power = data[0] != 0;
      break;
    case 0x02: // running
    case 0x03: // breathe
    case 0x04: // flame
    case 0x05: // rhythm
      if (len == 1) {
        if (data[0] != 0) state.mode = cmd;
        else if (state.mode == cmd) state.mode = 0;
      }
      break;
    case 0x06: // effect off
      if (len == 1 && data[0] != 0) state.mode = 0;
      break;
    case 0x07: // LED count +1 / -1 (relative)
      if (len == 1) {
        int next = (int)state.ledCount + (data[0] != 0 ? 1 : -1);
        state.ledCount = (uint8_t)constrain(next, 0, NUM_LEDS);
      }
      break;
    case 0x08: // color 1-9, 0 = off
      if (len == 1) {
        if (data[0] >= 1 && data[0] <= 9) {
          state.colorIndex = data[0];
          state.colorOn = true;
          state.mode = 0;
        } else if (data[0] == 0) {
          state.colorOn = false;
        }
      }
      break;
    case 0x0A: // extension: speed
      if (len == 1 && data[0] <= 100) state.speed = data[0];
      break;
    case 0x0B: // extension: query (no response, app does not read)
      break;
    case 0x0F: // extension sync: power, appMode, color, brightness, speed, count
      if (len == 6) {
        state.power = data[0] != 0;
        state.mode = appModeToCommand(data[1]);
        if (data[2] >= 1 && data[2] <= 9) {
          state.colorIndex = data[2];
          state.colorOn = true;
        } else {
          state.colorOn = false;
        }
        if (data[3] <= 100) state.brightness = data[3];
        if (data[4] <= 100) state.speed = data[4];
        state.ledCount = (uint8_t)constrain((int)data[5], 0, NUM_LEDS);
      }
      break;
    case 0x10: // brightness
      if (len == 1 && data[0] <= 100) state.brightness = data[0];
      break;
    case 0x11: // all off
      state.power = false;
      state.mode = 0;
      state.colorOn = false;
      break;
    case 0x12: // set LED count 0-255
      if (len == 1) state.ledCount = (uint8_t)constrain((int)data[0], 0, NUM_LEDS);
      break;
    case 0xFF: // self test
      if (len == 1 && data[0] != 0) selfTestUntil = millis() + 1500;
      break;
    default:
      break;
  }
}

uint8_t rxBuffer[96];
size_t rxLength = 0;

void feedBytes(const uint8_t* data, size_t len) {
  if (len > sizeof(rxBuffer) - rxLength) rxLength = 0;
  memcpy(rxBuffer + rxLength, data, len);
  rxLength += len;

  while (rxLength >= 4) {
    if (rxBuffer[0] != 0x11) {
      memmove(rxBuffer, rxBuffer + 1, --rxLength);
      continue;
    }
    uint8_t payloadLen = rxBuffer[2];
    if (payloadLen > 32) {
      memmove(rxBuffer, rxBuffer + 1, --rxLength);
      continue;
    }
    size_t total = 4 + payloadLen;
    if (rxLength < total) return;
    uint8_t expected = checksum(rxBuffer, total - 1);
    if (expected != rxBuffer[total - 1]) {
      memmove(rxBuffer, rxBuffer + 1, --rxLength);
      continue;
    }
    applyCommand(rxBuffer[1], rxBuffer + 3, payloadLen);
    memmove(rxBuffer, rxBuffer + total, rxLength - total);
    rxLength -= total;
  }
}

class ControlCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic) override {
    std::string value = characteristic->getValue();
    if (!value.empty()) {
      feedBytes(reinterpret_cast<const uint8_t*>(value.data()), value.size());
    }
  }
};

class ServerCallbacks : public NimBLEServerCallbacks {
  void onDisconnect(NimBLEServer* server) override {
    NimBLEDevice::startAdvertising();
  }
};

void renderLeds() {
  uint32_t now = millis();
  if (!state.power || !state.colorOn || state.ledCount == 0) {
    FastLED.clear(true);
    return;
  }
  if (now < selfTestUntil) {
    bool on = ((now / 180) % 2) == 0;
    fill_solid(leds, NUM_LEDS, on ? CRGB::White : CRGB::Black);
    FastLED.setBrightness(255);
    FastLED.show();
    return;
  }

  uint32_t interval = map(state.speed, 0, 100, 120, 12);
  if (now - lastRender < interval) return;
  lastRender = now;

  uint16_t count = state.ledCount;
  switch (state.mode) {
    case 2:
      hueOffset += 3;
      fill_rainbow(leds, count, hueOffset & 0xFF, 5);
      for (int i = 0; i < count; i++) {
        leds[i].nscale8(state.brightness * 255 / 100);
      }
      break;
    case 3: {
      float phase = (now % 4200) / 4200.0f;
      float wave = (sinf(phase * 2.0f * PI - PI / 2.0f) + 1.0f) * 0.5f;
      fill_solid(leds, count, colorForIndex(state.colorIndex));
      FastLED.setBrightness((uint8_t)(wave * state.brightness * 255 / 100));
      break;
    }
    case 4:
      fill_solid(leds, count, CRGB(255, 120, 40));
      FastLED.setBrightness((uint16_t)(150 + random8(90)) * state.brightness / 255);
      break;
    case 5: {
      uint8_t beat = (uint8_t)(180 + 75 * sinf(now / 180.0f));
      fill_solid(leds, count, colorForIndex(state.colorIndex));
      FastLED.setBrightness((uint16_t)beat * state.brightness / 100);
      break;
    }
    default:
      fill_solid(leds, count, colorForIndex(state.colorIndex));
      FastLED.setBrightness(state.brightness * 255 / 100);
      break;
  }
  if (count < NUM_LEDS) fill_solid(leds + count, NUM_LEDS - count, CRGB::Black);
  FastLED.show();
}

void setup() {
  Serial.begin(115200);
  FastLED.addLeds<LED_TYPE, LED_PIN, COLOR_ORDER>(leds, NUM_LEDS);
  FastLED.setMaxPowerInVoltsAndMilliamps(5, MAX_MILLIAMPS);
  FastLED.clear(true);

  uint8_t mac[6] = {0};
  esp_read_mac(mac, ESP_MAC_BT);
  snprintf(deviceName, sizeof(deviceName), "AuroraLight-%02X%02X", mac[4], mac[5]);

  NimBLEDevice::init(deviceName);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);
  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  NimBLEService* service = server->createService(SERVICE_UUID);
  controlChar = service->createCharacteristic(
      CONTROL_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  controlChar->setCallbacks(new ControlCallbacks());

  statusChar = service->createCharacteristic(STATUS_UUID, NIMBLE_PROPERTY::NOTIFY);
  service->createCharacteristic(INFO_UUID, NIMBLE_PROPERTY::READ);

  service->start();
  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setName(deviceName);
  advertising->start();
}

void loop() {
  renderLeds();
  delay(5);
}
