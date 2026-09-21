import subprocess
import time

ADB = "/Users/user/Library/Android/sdk/platform-tools/adb"
DEVICE = "emulator-5556"

def run_adb(cmd):
    full_cmd = [ADB, "-s", DEVICE] + cmd
    res = subprocess.run(full_cmd, capture_output=True, text=True)
    return res.stdout.strip()

def send_key(key_code):
    run_adb(["shell", "input", "keyevent", str(key_code)])
    time.sleep(1)

def tap(x, y):
    run_adb(["shell", "input", "tap", str(x), str(y)])
    time.sleep(1)

def screenshot(name):
    path = f"/Users/user/Desktop/Android_TV_Screenshots/{name}"
    with open(path, "wb") as f:
        subprocess.run([ADB, "-s", DEVICE, "exec-out", "screencap", "-p"], stdout=f)
    time.sleep(0.5)

print("Starting TV Navigation Automated Test...")

# 1. Start App
run_adb(["shell", "am", "start", "-n", "com.soloprono.aicleaner/com.example.MainActivity"])
time.sleep(2)

# Ensure onboarding is dismissed if present (tap top right Skip or press DPAD)
tap(1200, 60)
time.sleep(1)

screenshot("tv_test_01_dashboard.png")

# 2. Test Tab 2: Duplicates
tap(40, 240)
time.sleep(1.5)
screenshot("tv_test_02_duplicates.png")

# 3. Test Back Button -> Returns to Dashboard
send_key(4) # KEYCODE_BACK
time.sleep(1.5)
screenshot("tv_test_03_back_to_dashboard.png")

# 4. Test Tab 3: Swipe
tap(40, 320)
time.sleep(1.5)
screenshot("tv_test_04_swipe.png")

# 5. Test Back Button -> Returns to Dashboard
send_key(4) # KEYCODE_BACK
time.sleep(1.5)
screenshot("tv_test_05_back_to_dashboard.png")

# 6. Test Tab 4: Battery
tap(40, 420)
time.sleep(1.5)
screenshot("tv_test_06_battery.png")

# 7. Test Tab 5: Storage
tap(40, 520)
time.sleep(1.5)
screenshot("tv_test_07_storage.png")

# 8. Test Back Button -> Returns to Dashboard
send_key(4) # KEYCODE_BACK
time.sleep(1.5)
screenshot("tv_test_08_back_to_dashboard.png")

print("TV Navigation Test Completed Successfully!")
